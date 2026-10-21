package dev.lua.seatpreset

import android.content.Context
import android.os.SystemClock

/** Owner-enabled live recall; getter observation time is not OEM-cache freshness evidence. */
internal class LiveVehicle(private val context: Context,private val selectedAxes: Set<String>?=null,private val requestedAt: Long=SystemClock.elapsedRealtime(),private val trace: String="live-read"): VehicleAdapter {
  private val bridge=OemReadBridge(context)
  private val observed by lazy { bridge.seat() }
  override val source get()=observed.source
  override val coordinateFormat=SeatObservation.FORMAT
  override val recallValidated get()=SeatControl.enabled(context)
  override var resultMessage: String?=null
    private set
  override fun snapshot(): VehicleSnapshot {
    val raw=bridge.rawPark()
    SeatDiagnostics.event(context,trace,"park rawGearMode=${raw.first} rawEPB=${raw.second} sampledAtMs=${SystemClock.elapsedRealtime()}")
    val gear=when(raw.first) { 1->Gear.P;2->Gear.R;3->Gear.N;4->Gear.D;else->null }
    val brake=when(raw.second) { 3->true;1->false;else->null }
    val awake=(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
    return VehicleSnapshot(if(awake) gear else null,if(awake) brake else null,null,SystemClock.elapsedRealtime())
  }
  override fun failed(error: Exception) { SeatDiagnostics.event(context,trace,"failure="+SeatDiagnostics.failure(error)) }
  override fun capture()=bridge.seat()
  override fun accepts(position: Position)=position.source==source && position.coordinateFormat==coordinateFormat && SeatObservation.usable(position.coordinates)
  override fun apply(position: Position): Boolean {
    check(SeatControl.enabled(context)) { "Seat movement is off" }
    SeatDiagnostics.event(context,trace,SeatDiagnostics.position("target",position))
    val current=bridge.seat()
    SeatDiagnostics.event(context,trace,SeatDiagnostics.position("before",current))
    val started=requestedAt
    val plan=ManualSeatRecall.plan(current,position).filter { it.from!=it.to && (selectedAxes==null || it.name in selectedAxes) }
    SeatDiagnostics.event(context,trace,"candidatePlan=$plan")
    if (!SeatWritePolicy.allowed()) {
      resultMessage=SeatWritePolicy.BLOCK_REASON
      SeatDiagnostics.event(context,trace,"writeBlocked reason=${SeatWritePolicy.BLOCK_REASON} dispatched=0")
      return false
    }
    if(plan.isEmpty()) { resultMessage="Available axes already match the saved position";return true }
    // Bind and validate every method before sending the first axis. Never build a command backlog.
    val writers=plan.associate { it.name to bridge.bindAxisWriter(it.name) }
    try {
      SeatCommandGuard.dispatch(plan,started,SystemClock::elapsedRealtime,{ SeatControl.enabled(context) },::snapshot,
        { axis -> SeatDiagnostics.event(context,trace,"prepared axis=${axis.name} from=${axis.from} target=${axis.to} driverArea=1 requestedAtMs=$started") },
        { axis ->
          try { writers.getValue(axis.name)(axis.to) } catch(error: Exception) {
            SeatDiagnostics.event(context,trace,"setterFailed axis=${axis.name} driverArea=1 argument=${axis.to} cause="+SeatDiagnostics.failure(error))
            throw error
          }
          SeatDiagnostics.event(context,trace,"setterReturned axis=${axis.name} physicalCompletion=unverified")
        })
    } catch(e:Exception) {
      PrivateDiagnostics.record(context,"recall stopped type=${e.javaClass.simpleName} reason=${e.message}")
      throw e
    }
    resultMessage="Commands sent for ${plan.joinToString { it.name }}. Check physical movement."
    return true
  }
}

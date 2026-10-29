package dev.lua.seatpreset

import android.content.Context
import android.os.SystemClock

/** Independent coordinate recall using native signals with bounded observed feedback. */
internal class LiveVehicle(private val context:Context,private val selectedAxes:Set<String>?=null,
  private val requestedAt:Long=SystemClock.elapsedRealtime(),private val trace:String="live-read",
  private val origin:RecallOrigin=RecallOrigin.CARD,private val token:Long=SeatMotionState.token()):VehicleAdapter {
  private val parkBridge=OemReadBridge(context)
  override val source get()=FeedbackOemBridge.source(context)
  override val coordinateFormat=PositionFeedback.FORMAT
  override val recallValidated get()=SeatControl.enabled(context)
  override var resultMessage:String?=null
    private set
  override fun snapshot():VehicleSnapshot {
    val raw=parkBridge.rawPark()
    val awake=(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
    val gear=when(raw.first) { 1->Gear.P;2->Gear.R;3->Gear.N;4->Gear.D;else->null }
    val brake=when(raw.second) { 3->true;1->false;else->null }
    return VehicleSnapshot(if(awake) gear else null,if(awake) brake else null,null,SystemClock.elapsedRealtime())
  }
  override fun failed(error:Exception) { SeatDiagnostics.event(context,trace,"failure="+SeatDiagnostics.failure(error)) }
  override fun capture()=parkBridge.seat()
  override fun accepts(position:Position)=position.source==source && PositionFeedback.accepts(position)
  override fun apply(position:Position):Boolean {
    check(accepts(position)) { "Record this preset again using typed position readings" }
    val axes=selectedAxes ?: PositionFeedback.seatAxes.toSet()
    require(axes.isNotEmpty() && axes.all { it in PositionFeedback.seatAxes }) { "Unsupported movement axes" }
    check(SeatMotionState.operations.beginMotion()) { "A seat trial or load is already running" }
    try {
      FeedbackOemBridge(context,trace).use { sdk ->
        sdk.prepareSignals()
        SeatDiagnostics.event(context,trace,SeatDiagnostics.position("target",position))
        val target=position.coordinates.filterKeys { it in axes }
        val profile=SeatSignalProfile.read(context,source)
        SeatDiagnostics.event(context,trace,"directionProfile=$profile")
        var priorFrame:Map<String,PositionFeedback.Sample>?=null
        val port=object:FeedbackSeatRecall.Port {
          override fun clock()=SystemClock.elapsedRealtime()
          override fun pause(ms:Long)=Thread.sleep(ms)
          override fun enabled()=SeatControl.enabled(context)
          override fun active()=SeatMotionState.current(token) && SeatMotionState.surface(origin,context)
          override fun vehicle()=snapshot()
          override fun read()=sdk.read().also { frame ->
            if(frame!=priorFrame) { SeatDiagnostics.event(context,trace,"feedback-frame sampledAtMs=${clock()} raw=$frame freshness=unverified");priorFrame=frame.toMap() }
          }
          override fun increasingSignal(axis:String)=profile.getValue(axis)
          override fun signal(axis:String,direction:Int)=sdk.signal(axis,direction)
          override fun audit(detail:String) {
            SeatMotionState.progress=when { detail.startsWith("prepare ")->"Loading "+detail.substringAfter("axis=").substringBefore(' ')+"…";else->SeatMotionState.progress }
            SeatDiagnostics.event(context,trace,detail+" sampledAtMs="+clock())
          }
        }
        val outcome=FeedbackSeatRecall.run(port,target,requestedAt)
        val mirrors=position.coordinates.keys.any { it.startsWith("mirror.") }
        resultMessage="Seat feedback matches the preset"+(if(mirrors) ". Mirrors were recorded but not moved." else ".")
        SeatDiagnostics.event(context,trace,"feedbackMatch=true pulses=${outcome.pulses} axes=${outcome.axes} mirrorCommands=0 physicalCompletion=owner-check")
        return true
      }
    } catch(error:Exception) {
      resultMessage="Seat load stopped: "+(error.message ?: error.javaClass.simpleName).take(180)
      throw error
    } finally { SeatMotionState.operations.endMotion();SeatMotionState.progress="" }
  }
}

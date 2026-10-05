package dev.lua.seatpreset

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import java.util.concurrent.Executors

/** Explicit foreground, one-axis owner-operated trial. No pending command survives leaving it. */
class SeatMovementTrialActivity : Activity() {
  private val worker = Executors.newSingleThreadExecutor()
  private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
  private val trial = SeatMotionTrial()
  @Volatile private var foreground = false
  private lateinit var output: TextView
  private lateinit var bridge: OemReadBridge
  private lateinit var axis: Spinner
  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    if (!StorageAccess.unlocked(this) || !PresetStore(this).developerMode || BuildConfig.DEMO) { finish(); return }
    bridge=OemReadBridge(this)
    val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,24,32,24);setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun label(value:String,size:Float)=TextView(this).apply { text=value;textSize=size;setTextColor(getColor(R.color.matte_silver));setPadding(0,12,0,12) }
    fun button(value:String,click:()->Unit)=Button(this).apply { text=value;isAllCaps=false;setOnClickListener { runCatching(click).onFailure { output.text=it.message?:"Test unavailable" } } }
    page.addView(label("Supervised seat movement trial",28f))
    page.addView(label("Owner-operated car only. First use native controls to select A, then change ONLY one driver-seat axis by a small amount to B, then return to A. Record each position below. Confirm the readings track that axis and the native controls interrupt movement. This test sends ONE percentage command to driver area 1. It does not move mirrors or enable normal recall.",18f))
    axis=Spinner(this).apply { adapter=ArrayAdapter(this@SeatMovementTrialActivity,android.R.layout.simple_spinner_dropdown_item,SeatObservation.axes.toList());onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long) { trial.reset() }
      override fun onNothingSelected(parent:AdapterView<*>?) {}
    } };page.addView(axis)
    output=label("Record A → B → A using the native seat controls. No app command is sent during these reads.",22f);page.addView(output)
    page.addView(button("Record next native position · A / B / A") { val selected=axis.selectedItem.toString();task {
      val p=bridge.seat();val at=SystemClock.elapsedRealtime()
      runOnUiThread { if(foreground) { trial.record(p,at);output.text=p.coordinates.toString()+"\nAxis verified in this session: ${trial.validated(selected)}" } }
    } })
    page.addView(button("Test one axis from saved preset…") { confirmPhysical() })
    page.addView(button("Back · discard test") { finish() })
    setContentView(ScrollView(this).apply { addView(page) })
  }
  private fun task(work:()->Unit) {
    if(!foreground || !busy.compareAndSet(false,true)) return
    worker.execute { try { work() } catch(e:Exception) { trial.reset();runOnUiThread { if(foreground) output.text="Test stopped: ${e.cause?.javaClass?.simpleName?:e.message}. No further commands will be sent." } } finally { busy.set(false) } }
  }
  private fun confirmPhysical() {
    check(!busy.get()) { "Wait for the current read" }
    val selected=axis.selectedItem.toString()
    check(trial.validated(selected)) { "Record a matching native A → B → A for this axis first" }
    AlertDialog.Builder(this).setTitle("Confirm the physical test")
      .setMessage("Are you in the driver's seat, in P, parking brake applied, with clear space? Have you verified that native seat controls interrupt movement and that the readings track this driver's axis? You must remain at the controls. Cancel if any condition is uncertain.")
      .setNegativeButton("Cancel",null).setPositiveButton("Confirmed") { _,_->
        val all=runCatching { PresetStore(this).all() }.getOrElse { output.text="Preset storage unavailable";return@setPositiveButton }.filter { it.position?.coordinateFormat==SeatObservation.FORMAT }
        AlertDialog.Builder(this).setTitle("Select the saved test position").setItems(all.map { it.name }.toTypedArray()) { _,i ->
          task {
            val target=all[i].position!!;val current=bridge.seat()
            val writer=bridge.bindAxisWriter(selected)
            check(bridge.parkedVetoClear()) { "OEM P/brake readings are unavailable or do not match" }
            // Validate the small delta before presenting the final button.
            trial.prepare(selected,current,target,SystemClock.elapsedRealtime())
            runOnUiThread { if(foreground) {
              AlertDialog.Builder(this).setTitle("Move the driver's $selected axis now?")
                .setMessage("${current.coordinates.getValue(selected)} → ${target.coordinates.getValue(selected)}. Only this axis, driver area 1. Press native seat controls if movement is wrong. The app cannot guarantee cancellation. Reconfirm P and parking brake at the moment you press Move. This is a supervised experiment, not validated recall.")
                .setNegativeButton("Cancel",null).setPositiveButton("Move now") { _,_->
                  val confirmed=SystemClock.elapsedRealtime()
                  task {
                    val fresh=bridge.seat()
                    check(bridge.parkedVetoClear()) { "OEM P/brake veto failed" }
                    trial.prepare(selected,fresh,target,confirmed)
                    // Fresh physical evidence is the owner's final confirmation, not the OEM cache.
                    val physical=VehicleSnapshot(Gear.P,true,null,confirmed)
                    val command=trial.consume(SystemClock.elapsedRealtime(),physical,foreground)
                    check(foreground && SystemClock.elapsedRealtime()-confirmed <= RecallPolicy.MAX_AGE_MS) { "Test expired before command" }
                    PrivateDiagnostics.record(this,"supervised axis command START axis=${command.axis} target=${command.target}")
                    // Do not hold the trial lock across an OEM binder call or block lifecycle cancellation.
                    check(foreground && SystemClock.elapsedRealtime()-confirmed <= RecallPolicy.MAX_AGE_MS && bridge.parkedVetoClear()) { "Conditions changed before command" }
                    check(foreground && SystemClock.elapsedRealtime()-confirmed <= RecallPolicy.MAX_AGE_MS) { "Confirmation expired" }
                    writer(command.target)
                    PrivateDiagnostics.record(this,"supervised axis command RETURNED axis=${command.axis}; completion unverified")
                    val after=bridge.seat()
                    runOnUiThread { if(foreground) output.text="One $selected command sent. Getter readback: ${after.coordinates.getValue(selected)}. Confirm actual movement yourself. Completion is not proven. No more commands are queued." }
                  }
                }.show()
            } }
          }
        }.setNegativeButton("Cancel",null).show()
      }.show()
  }
  override fun onResume() { super.onResume();foreground=true }
  override fun onStop() { foreground=false;synchronized(trial) { trial.reset() };super.onStop() }
  override fun onDestroy() { worker.shutdownNow();super.onDestroy() }
}

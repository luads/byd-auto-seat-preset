package dev.lua.seatpreset

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.*
import java.util.UUID
import java.util.concurrent.Executors

/** Owner-triggered reads around native adjustments only. No writer or recall controller is used. */
class SeatObservationTrialActivity : Activity() {
  private val deadlineHandler = Handler(Looper.getMainLooper())
  private var deadlineTask: Runnable? = null
  private var ticket: SeatTrialReadGuard.Ticket? = null
  private val trial = NativeSeatObservationTrial()
  @Volatile private var active = false
  @Volatile private var generation = 0
  private var trace = "native-seat-" + UUID.randomUUID()
  private var selectedAxis = "horizontal"
  private var fresh = true
  private lateinit var bridge: OemReadBridge
  private lateinit var output: TextView
  private lateinit var accessStatus:TextView
  private lateinit var setupButton:Button
  private lateinit var readButton: Button
  private lateinit var cancelButton: Button
  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    if (!StorageAccess.unlocked(this) || BuildConfig.DEMO) { finish(); return }
    bridge = OemReadBridge(this)
    val page = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24)
      setBackgroundColor(getColor(R.color.matte_canvas))
    }
    fun label(value: String, size: Float = 18f) = TextView(this).apply {
      text = value; textSize = size; setTextColor(getColor(R.color.matte_silver)); setPadding(0, 12, 0, 12)
    }
    fun button(value: String, action: () -> Unit) = Button(this).apply {
      text = value; isAllCaps = false
      layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12; bottomMargin = 12 }
      setOnClickListener { action() }
    }
    page.addView(label("Native seat observation", 28f))
    page.addView(label("Park in P with the parking brake set. Start at A, read, use the car's native controls for one small adjustment on the selected axis to B, read, then return to A and read. Keep native controls within reach and clearance around the seat. This screen only reads. It never moves the seat or changes a preset."))
    page.addView(label("Choose the axis you will change. Changing it starts a new trial. Values are raw candidates with unverified units."))
    val axes = RadioGroup(this)
    SeatObservation.axes.forEach { axis ->
      val choice = RadioButton(this).apply {
        id = android.view.View.generateViewId(); text = axis.replaceFirstChar { it.uppercaseChar() }
        setTextColor(getColor(R.color.matte_silver)); minHeight = 64
      }
      axes.addView(choice)
      if (axis == selectedAxis) choice.isChecked = true
      choice.setOnCheckedChangeListener { _, checked -> if (checked && selectedAxis != axis) {
        selectedAxis = axis; reset()
      } }
    }
    page.addView(axes)
    accessStatus=label("");page.addView(accessStatus)
    setupButton=button("Set up missing read access") {
      android.app.AlertDialog.Builder(this).setTitle("Set up vehicle readings?")
        .setMessage("Grant this app missing read permissions through the local setup connection. Approve the Android prompt if shown. This does not grant seat-write access or move anything.")
        .setNegativeButton("Cancel",null).setPositiveButton("Set up") { _,_-> setupReads() }.show()
    };page.addView(setupButton)
    output = label("Ready for A. Reads and results go to the private report. Export that report after the trial.", 20f)
    page.addView(output)
    readButton = button("Read ${trial.stage()}") { read() }; page.addView(readButton)
    cancelButton = button("Cancel read") { cancelRead() }; cancelButton.isEnabled = false; page.addView(cancelButton)
    page.addView(button("Start a new observation trial") { reset() })
    page.addView(button("Back") { finish() })
    page.addView(label("You can leave this screen to use native seat settings and return. Samples remain only in memory for up to ten minutes. Leaving during a read discards that read. Background, startup and restart never start a trial or send a command."))
    setContentView(ScrollView(this).apply { addView(page) })
  }
  private fun reset() {
    generation++; trial.reset(); fresh = true; trace = "native-seat-" + UUID.randomUUID()
    if (::readButton.isInitialized) { readButton.text = "Read ${trial.stage()}"; readButton.isEnabled = !readGuard.busy() }
    if (::output.isInitialized) output.text = "New $selectedAxis observation. Ready for A. No command sent."
  }
  private fun requirePark(): Pair<Int?, Int?> {
    check(active && (getSystemService(POWER_SERVICE) as PowerManager).isInteractive) { "Keep the trial visible and the screen on" }
    val began = SystemClock.elapsedRealtime()
    val raw = bridge.rawPark()
    check(active && SystemClock.elapsedRealtime() - began <= RecallPolicy.MAX_AGE_MS) { "Park readings took too long. Read again." }
    check(GearModeRead.parked(raw.first, raw.second)) { "Fresh P and parking-brake readings are required" }
    return raw
  }
  private fun candidates(traceAtRead: String): Map<String, Double?> {
    val values = linkedMapOf<String, Double?>()
    bridge.percentageSeatCandidates().forEach { (axis, value) -> values["setting.$axis"] = value }
    // Failure of this optional reader must leave the percentage evidence available.
    bridge.horizontalProperty()?.let { (raw,evidence) ->
      values["property.horizontal"]=raw
      SeatDiagnostics.event(this,traceAtRead,"candidate axis=horizontal value=$raw ${evidence.describe()}")
    }
    if("property.horizontal" !in values) values["property.horizontal"]=null
    val absolute = runCatching { bridge.absoluteSeatCandidates() }.getOrElse {
      SeatDiagnostics.event(this, traceAtRead, "absolute unavailable cause=" + SeatDiagnostics.failure(it)); emptyMap()
    }
    ABSOLUTE_NAMES.forEach { method -> values["bodywork.$method"] = absolute[method] }
    return values
  }
  private fun read() {
    if (!active) return
    showAccess()
    val request = readGuard.begin(SystemClock.elapsedRealtime())
    if (request == null) {
      output.text = "An earlier vehicle read is still running. No second read was started. Restart the app if it does not return."
      return
    }
    ticket = request
    val token = generation; val phase = trial.stage(); val axis = selectedAxis; val traceAtRead = trace
    val inventory = fresh
    readButton.isEnabled = false; cancelButton.isEnabled = true
    output.text = "Reading $phase. Keep the seat stopped. The read deadline is 20 seconds."
    val timeoutTask = Runnable {
      if (readGuard.expire(request, SystemClock.elapsedRealtime())) {
        generation++; cancelButton.isEnabled = false
        output.text = "Read timed out after 20 seconds. Its result will be discarded. No second read can start until it returns. Restart the app if it remains stuck."
        SeatDiagnostics.event(this, traceAtRead, "phase=$phase readTimeout deadlineMs=${request.deadlineMs} resultDiscarded=true noCommand=true")
      }
    }
    deadlineTask = timeoutTask; deadlineHandler.postDelayed(timeoutTask, 20_000)
    worker.execute {
      val result = runCatching {
        val began = SystemClock.elapsedRealtime()
        val before = requirePark()
        if (inventory) runCatching { SeatSignatureInventory.ensure(this) { bridge.seatSignatures() } }
          .fold({ SeatDiagnostics.event(this,traceAtRead,"inventoryKey=$it storedSeparately=true") },
            { SeatDiagnostics.event(this,traceAtRead,"inventoryUnavailable="+SeatDiagnostics.failure(it)) })
        val first = candidates(traceAtRead); Thread.sleep(200); val second = candidates(traceAtRead)
        val after = requirePark(); val ended = SystemClock.elapsedRealtime()
        check(active && token == generation) { "Read discarded after screen, cancellation or trial change" }
        SeatDiagnostics.event(this, traceAtRead, "phase=$phase intendedNativeAxis=$axis beganMs=$began endedMs=$ended rawParkBefore=$before rawParkAfter=$after noCommand=true")
        first.keys.forEach { route -> SeatDiagnostics.event(this, traceAtRead, "phase=$phase route=$route first=${first[route]} second=${second[route]} units=unverified") }
        check(active && token == generation) { "Read discarded after screen or trial change" }
        check(first == second) { "Values changed during the read. Stop adjusting the seat and read this phase again." }
        check(second.values.any { it != null }) { "No seat reading available. Set up vehicle readings and try again." }
        NativeSeatObservationTrial.Sample(ended, second)
      }
      val accepted = readGuard.finish(request, SystemClock.elapsedRealtime())
      runOnUiThread {
        deadlineHandler.removeCallbacks(timeoutTask)
        if (deadlineTask === timeoutTask) deadlineTask = null
        if (ticket == request) {
          ticket = null; cancelButton.isEnabled = false
          if (!accepted && active && !isFinishing) output.text = "Discarded read returned. You can start a new explicit read. No phase was added."
        }
        if (!isFinishing && active) readButton.isEnabled = !readGuard.busy() && !trial.complete()
        if (!accepted) SeatDiagnostics.event(this, traceAtRead, "phase=$phase lateOrCancelledReadReturned resultDiscarded=true noCommand=true")
        if (!isFinishing && active && token == generation && accepted) {
          result.fold({ sample ->
            runCatching { trial.add(axis, sample) }.fold({ summary ->
              fresh = false; output.text = summary
              summary.lineSequence().forEach { SeatDiagnostics.event(this, traceAtRead, it) }
              readButton.text = "Read ${trial.stage()}"
              if (summary.startsWith("Native ")) readButton.isEnabled = false
            }, { failure -> output.text = failure.message ?: "Start a new trial" })
          }, { failure ->
            output.text = "Read unavailable: ${failure.message ?: failure.javaClass.simpleName}. No command sent."
            SeatDiagnostics.event(this, traceAtRead, "phase=$phase rejected=" + SeatDiagnostics.failure(failure))
          })
        }
      }
    }
  }
  private fun showAccess() {
    val missing=VehicleReadSetup.missing(this)
    accessStatus.text=if(missing.isEmpty()) "Read permissions granted. OEM values and cache freshness still need verification."
      else "Missing read access: "+missing.joinToString { it.substringAfter("BYDAUTO_") }+". Affected routes remain unavailable; available routes can still be observed."
    setupButton.visibility=if(missing.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
  }
  private fun setupReads() {
    val request=readGuard.begin(SystemClock.elapsedRealtime()) ?: return
    ticket=request;readButton.isEnabled=false;setupButton.isEnabled=false
    output.text="Setting up read permissions…"
    worker.execute {
      val result=runCatching { VehicleReadSetup.enable(this) }.getOrElse {
        SeatDiagnostics.event(this,trace,"readSetupFailed="+SeatDiagnostics.failure(it))
        "Setup did not finish. Check the local connection and retry."
      }
      readGuard.finish(request,SystemClock.elapsedRealtime())
      runOnUiThread { if(!isFinishing) {
        ticket=null;setupButton.isEnabled=true;showAccess();output.text=result
        readButton.isEnabled=active && !trial.complete() && !readGuard.busy()
      } }
    }
  }
  private fun cancelRead() {
    val request = ticket ?: return
    if (!readGuard.cancel(request)) return
    generation++; cancelButton.isEnabled = false
    output.text = "Read cancelled. Its result will be discarded. The OEM call is not interrupted. Wait for it to return, or restart the app if it remains stuck."
    SeatDiagnostics.event(this, trace, "readCancelled resultDiscarded=true noCommand=true")
  }
  override fun onResume() {
    super.onResume(); active = true
    if(::accessStatus.isInitialized) showAccess()
    if (::readButton.isInitialized) readButton.isEnabled = !readGuard.busy() && !trial.complete()
    if (readGuard.busy() && ticket == null) output.text = "An earlier vehicle read is still running. Wait for it to return, or restart the app if it remains stuck. No second read was started."
  }
  override fun onPause() { active = false; generation++; super.onPause() }
  override fun onStop() { active = false; super.onStop() }
  override fun onDestroy() {
    active = false
    ticket?.let { readGuard.cancel(it) }
    deadlineTask?.let { deadlineHandler.removeCallbacks(it) }
    super.onDestroy()
  }
  companion object {
    // One outstanding call per process, including across Activity recreation. Never interrupt OEM code.
    private val readGuard = SeatTrialReadGuard()
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "native-seat-read").apply { isDaemon = true } }
    private val ABSOLUTE_NAMES = listOf("getDriverSeatHorizontalPosition", "getDriverSeatBackrestPosition", "getDriverSeatHeightPosition",
      "getDriverSeatSitpointPosition", "getDriverSeatLegholderPosition", "getDriverSeatHeadrestHorizontalPosition", "getDriverSeatHeadrestVerticalPosition")
  }
}

package dev.lua.seatpreset

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Structured optional native-signal trial. Production recalls use the same native signal bridge. */
class BackrestTestActivity:Activity() {
  private val main=Handler(Looper.getMainLooper())
  @Volatile private var active=false
  @Volatile private var generation=0
  private var trace="native-trial-"+java.util.UUID.randomUUID()
  private var selected="backrest"
  private var observingUntil=0L
  private var hasAttempt=false
  private var manual=false
  private var observed=false
  private var pulseBefore:Map<String,PositionFeedback.Sample>?=null
  private var pulseAfter:Map<String,PositionFeedback.Sample>?=null
  private var pulseAxis="backrest"
  private var pulseDirection=1
  private lateinit var directionConfirm:Button
  private lateinit var output:TextView
  private val controls=mutableListOf<View>()
  private lateinit var physical:Button
  private lateinit var restored:Button
  private lateinit var end:Button
  private val parkBridge by lazy { OemReadBridge(applicationContext) }
  private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  override fun onCreate(state:Bundle?) {
    super.onCreate(state)
    if(!StorageAccess.unlocked(this)) { finish();return }
    val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(28),dp(20),dp(28),dp(28));setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun text(value:String,size:Float=18f)=TextView(this).apply { text=value;textSize=size;setTextColor(getColor(R.color.matte_silver));setPadding(0,dp(8),0,dp(8)) }
    fun button(value:String,block:()->Unit)=Button(this).apply { text=value;isAllCaps=false;minHeight=dp(64);layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6);bottomMargin=dp(6) };setOnClickListener { block() } }
    page.addView(text(if(BuildConfig.DEMO) "Seat trials · simulation" else "Seat trials",28f))
    page.addView(text("Try one native 100 ms pulse, then check readback and physical stop. Keep native controls ready. Trials are optional; preset loading is enabled."))
    val wide=resources.configuration.screenWidthDp>=900
    val columns=LinearLayout(this).apply { orientation=if(wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
    val actions=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    if(wide) {
      columns.addView(actions,LinearLayout.LayoutParams(0,-2,1f))
      columns.addView(details,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(20) })
    } else { columns.addView(actions);columns.addView(details) }
    page.addView(columns)
    actions.addView(Spinner(this).apply {
      adapter=ArrayAdapter(this@BackrestTestActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Backrest","Fore/aft","Height"))
      minimumHeight=dp(60);controls+=this
      onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { val axis=listOf("backrest","horizontal","height")[position];if(axis!=selected) { selected=axis;observed=false;hasAttempt=false;pulseBefore=null;pulseAfter=null;output.text="Read $selected before testing.";update() } }
        override fun onNothingSelected(parent:AdapterView<*>?) {}
      }
    })
    val setup=button("Set up seat access") {
      AlertDialog.Builder(this).setTitle("Grant native seat access?")
        .setMessage("Grant missing read access and this app's SETTING_SET permission through the local setup connection. No movement, native preset change or automatic recall. The movement toggle remains separate.")
        .setNegativeButton("Cancel",null).setPositiveButton("Set up") { _,_-> start("Setting up native access…") {
          if(BuildConfig.DEMO) "Simulation needs no OEM grant." else NativeSeatAccess.enable(applicationContext)
        } }.show()
    };actions.addView(setup);controls+=setup
    val read=button("Read seat and mirror positions") { readPositions() };actions.addView(read);controls+=read
    val row=LinearLayout(this)
    val increase=button("Forward / up · 100 ms") { confirm(1) }
    val decrease=button("Back / down · 100 ms") { confirm(2) }
    increase.tag="pulse";decrease.tag="pulse";controls+=increase;controls+=decrease
    row.addView(increase,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(10) });row.addView(decrease,LinearLayout.LayoutParams(0,-2,1f));actions.addView(row)
    actions.addView(button("Stop / cancel") { generation++;SeatMotionState.cancel();endObservation() })
    val mirrors=button("Compare mirror read routes") { compareMirrors() };actions.addView(mirrors);controls+=mirrors
    if(BuildConfig.DEMO) { val simulate=button("Simulate a saved seat load") { simulateLoad() };actions.addView(simulate);controls+=simulate }
    output=text("Read first. Seat and mirror data stays in the private report and can be sent through ADB Lab. Only the backrest forward/release path has an owner-confirmed field result so far.",19f);details.addView(output)
    physical=button("I used a physical control") { mark("physical-control") };details.addView(physical)
    restored=button("I restored my native preset") { mark("native-preset-restored") };details.addView(restored)
    directionConfirm=button("Confirm observed direction and stop") { confirmDirection() };details.addView(directionConfirm)
    end=button("End observation") { endObservation() };details.addView(end)
    page.addView(button("Back") { finish() })
    setContentView(ScrollView(this).apply { addView(page) });update()
  }
  private fun update() {
    if(!::output.isInitialized) return
    val ready=active && !busy.get() && observingUntil==0L
    controls.forEach { it.isEnabled=ready && (it.tag!="pulse" || observed) }
    physical.isEnabled=hasAttempt;restored.isEnabled=hasAttempt
    end.visibility=if(observingUntil>0) View.VISIBLE else View.GONE
    directionConfirm.isEnabled=active && !busy.get() && !manual && pulseBefore!=null && pulseAfter!=null
  }
  private fun state():VehicleSnapshot {
    if(BuildConfig.DEMO) return DemoVehicle(this).snapshot()
    val raw=parkBridge.rawPark()
    val power=getSystemService(POWER_SERVICE) as android.os.PowerManager
    return VehicleSnapshot(if(raw.first==1 && power.isInteractive) Gear.P else null,if(raw.second==3) true else null,null,SystemClock.elapsedRealtime())
  }
  private fun read():Map<String,PositionFeedback.Sample> = if(BuildConfig.DEMO) synchronized(demo) { demo.toMap() }
    else FeedbackOemBridge(applicationContext,trace).use { it.read() }
  private fun log(stage:String,values:Map<String,PositionFeedback.Sample>) { SeatDiagnostics.event(this,trace,"stage=$stage axis=$selected sampledAtMs=${SystemClock.elapsedRealtime()} values=$values ownerIntervened=$manual freshness=unverified") }
  private fun start(label:String,block:()->String) {
    if(!active || !busy.compareAndSet(false,true)) return
    val token=generation;output.text=label;update()
    worker.execute {
      val result=runCatching(block).onFailure { SeatDiagnostics.event(this,trace,"trialFailed="+SeatDiagnostics.failure(it)) }
      busy.set(false)
      main.post { if(active && token==generation) { output.text=result.getOrElse { "Stopped: ${it.message}" };update() } }
    }
  }
  private fun readPositions() {
    trace="native-trial-"+java.util.UUID.randomUUID();manual=false;hasAttempt=false;observed=false;pulseBefore=null;pulseAfter=null
    start("Reading typed positions…") {
      val data=read();log("before",data)
      main.post { if(active) { observed=true;update() } }
      data.entries.joinToString("\n") { (axis,sample)->"$axis: ${sample.value} · cache ${sample.cacheAtNs}" }+"\nRead time is not proof of fresh ECU data. Mirrors will not be moved."
    }
  }
  private fun confirm(direction:Int) {
    if(!observed || busy.get() || !active) return
    val axis=selected;val token=generation
    val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),dp(10)) }
    root.addView(TextView(this).apply { text="Driver $axis · native signal $direction, then release=0 after a nominal 100 ms. Physical travel, other directions and axes remain experimental. No percentage target, native-bank change or mirror command. Native controls must remain within reach.";textSize=18f })
    val check=CheckBox(this).apply { text="I am in P, parking brake applied, with clearance and native controls ready.";minHeight=dp(64) };root.addView(check)
    val dialog=AlertDialog.Builder(this).setTitle(if(BuildConfig.DEMO) "Simulate one pulse?" else "Run one native pulse?")
      .setView(ScrollView(this).apply { addView(root) }).setNegativeButton("Cancel",null)
      .setPositiveButton(if(BuildConfig.DEMO) "Simulate" else "Move once") { _,_->if(active && token==generation && check.isChecked) pulse(axis,direction) }.create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false;check.setOnCheckedChangeListener { _,on->dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=on } };dialog.show()
  }
  private fun pulse(axis:String,direction:Int) {
    val requestedAt=SystemClock.elapsedRealtime();val token=generation;observed=false;hasAttempt=true
    start("Checking P, brake and native access…") {
      check(SeatMotionState.operations.beginMotion()) { "Another seat load or trial is running" }
      try {
        val before=read();pulseBefore=before;pulseAfter=null;pulseAxis=axis;pulseDirection=direction;manual=false;log("before-pulse",before)
        val sdk=if(BuildConfig.DEMO) null else FeedbackOemBridge(applicationContext,trace)
        sdk.use {
          it?.prepareSignals()
          check(SeatControl.enabled(this)) { "Enable Seat movement first" }
          RecallPolicy.blockReason(state(),SystemClock.elapsedRealtime())?.let { reason->error(reason) }
          check(active && token==generation && SystemClock.elapsedRealtime()-requestedAt<=RecallPolicy.MAX_AGE_MS) { "Trial cancelled or request expired" }
          SeatDiagnostics.event(this,trace,"ownerConfirmed=true nativeSignal axis=$axis direction=$direction release=0 pulseMs=100 driverArea=1")
          // Logging is complete before the final P/foreground check and signal.
          RecallPolicy.blockReason(state(),SystemClock.elapsedRealtime())?.let { reason->error(reason) }
          check(active && token==generation && SeatControl.enabled(this)) { "Trial cancelled" }
          val at=SystemClock.elapsedRealtime()
          try {
            if(BuildConfig.DEMO) synchronized(demo) { val old=demo.getValue(axis);demo[axis]=PositionFeedback.Sample(old.value+if(direction==1) 1 else -1,old.cacheAtNs+1) }
            else it!!.signal(axis,direction)
            Thread.sleep(FeedbackSeatRecall.PULSE_MS)
          } finally {
            val released=runCatching { it?.signal(axis,0) }
            SeatDiagnostics.event(this,trace,"nativeRelease axis=$axis attempted=true returned=${released.isSuccess} elapsedMs=${SystemClock.elapsedRealtime()-at} physicalStop=owner-check")
            released.getOrThrow()
          }
        }
        main.post { if(active && token==generation) { observingUntil=SystemClock.elapsedRealtime()+10_000;update();observe(token) } }
        "One pulse and release requested. Observing. Confirm the actual movement and stop; SDK returns are not physical completion."
      } finally { SeatMotionState.operations.endMotion() }
    }
  }
  private fun observe(token:Int) {
    if(!active || token!=generation || observingUntil==0L) return
    if(SystemClock.elapsedRealtime()>=observingUntil) { endObservation();return }
    if(!busy.compareAndSet(false,true)) { main.postDelayed({ observe(token) },500);return }
    worker.execute {
      val result=runCatching { read() }.onSuccess { pulseAfter=it;log("after",it) }.onFailure { SeatDiagnostics.event(this,trace,"readbackFailed="+SeatDiagnostics.failure(it)) }
      busy.set(false)
      main.post {
        if(active) update()
        if(active && token==generation && observingUntil>0) {
        result.onSuccess {
          val rendered=it.entries.joinToString("\n") { (axis,value)->"$axis: ${value.value}" }+if(manual) "\nOwner intervention marked; physical-action time is unknown." else "\nObservation only. Cache timestamps are in the private report."
          if(output.text.toString()!=rendered) output.text=rendered
        }
        update();main.postDelayed({ observe(token) },500)
      } }
    }
  }
  private fun mark(kind:String) {
    manual=true;update()
    SeatDiagnostics.event(this,trace,"ownerIntervention=$kind reportedAtMs=${SystemClock.elapsedRealtime()} earlierSamplesMayIncludeIntervention=true actualActionTime=unknown")
    output.text="Owner intervention recorded. The marker sends no vehicle command."
  }
  private fun confirmDirection() {
    if(manual || busy.get()) return
    val before=pulseBefore ?: return
    val after=pulseAfter ?: return
    val inferred=runCatching { SeatSignalProfile.infer(pulseAxis,pulseDirection,before,after) }.getOrElse { output.text="Direction not usable: ${it.message}";return }
    AlertDialog.Builder(this).setTitle("Confirm $pulseAxis behavior?")
      .setMessage("Confirm only the selected seat axis moved briefly and stopped by itself, with no physical intervention. Signal $inferred will mean a higher saved coordinate on this firmware. This changes no saved coordinates and sends no command.")
      .setNegativeButton("Cancel",null).setPositiveButton("Confirm") { _,_->
        runCatching { SeatSignalProfile.save(this,if(BuildConfig.DEMO) "simulation" else FeedbackOemBridge.source(this),pulseAxis,inferred) }
          .onSuccess { SeatDiagnostics.event(this,trace,"ownerConfirmedDirection axis=$pulseAxis increasingSignal=$inferred pulseSignal=$pulseDirection before=$before after=$after physicalStop=owner-confirmed");output.text="Direction saved for this firmware. No movement was sent." }
          .onFailure { output.text="Direction was not saved: ${it.message}" }
      }.show()
  }
  private fun endObservation() { observingUntil=0;main.removeCallbacksAndMessages(null);observed=false;output.text="Observation ended. Review physical behavior and the private report.";update() }
  private fun compareMirrors() {
    start("Comparing fixed mirror getter routes…") {
      if(BuildConfig.DEMO) { log("mirror-read-simulation",read());"Simulation: all four mirror readings recorded. No mirror command." }
      else {
        val report=MirrorReadProbe.read(applicationContext,trace)
        "Mirror comparison saved: four axes, two SDK sources, direct and generic getters, with typed before/after readings. No mirror command. Full results are in the private report.\n\n"+report.lineSequence().filter { it.startsWith("mirror-typed stage=after") }.take(4).joinToString("\n")
      }
    }
  }
  private fun simulateLoad() {
    val requested=SystemClock.elapsedRealtime();pulseBefore=null;pulseAfter=null;hasAttempt=false
    start("Simulating the independent coordinate loader…") {
      val target=read().filterKeys { it in PositionFeedback.seatAxes }.mapValues { it.value.value+2 }
      val token=generation
      val port=object:FeedbackSeatRecall.Port {
        override fun clock()=SystemClock.elapsedRealtime()
        override fun pause(ms:Long)=Thread.sleep(ms)
        override fun enabled()=SeatControl.enabled(this@BackrestTestActivity)
        override fun active()=active && token==generation
        override fun vehicle()=state()
        override fun read()=this@BackrestTestActivity.read()
        override fun signal(axis:String,direction:Int) { if(direction!=0) synchronized(demo) { val old=demo.getValue(axis);demo[axis]=PositionFeedback.Sample(old.value+if(direction==1) 1 else -1,old.cacheAtNs+1) } }
        override fun audit(detail:String)=SeatDiagnostics.event(this@BackrestTestActivity,trace,"simulation $detail")
      }
      val result=FeedbackSeatRecall.run(port,target,requested)
      "Simulated saved seat matched after ${result.pulses} pulses. No OEM command."
    }
  }
  override fun onResume() { super.onResume();active=true;update() }
  override fun onPause() { active=false;generation++;observingUntil=0;main.removeCallbacksAndMessages(null);super.onPause() }
  companion object {
    private val busy=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor { task->Thread(task,"native-seat-trial").apply { isDaemon=true } }
    private val demo=PositionFeedback.properties.keys.associateWith { PositionFeedback.Sample(50.0,100L) }.toMutableMap()
  }
}

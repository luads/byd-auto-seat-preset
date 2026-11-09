package dev.lua.seatpreset

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Foreground read-only comparison. Neither worker binds or invokes a vehicle setter. */
class SeatFeedbackActivity:Activity() {
  private val main=Handler(Looper.getMainLooper())
  @Volatile private var visible=false
  private var selected="backrest"
  private var current:Run?=null
  private lateinit var output:TextView
  private lateinit var start:Button
  private lateinit var stop:Button
  private lateinit var axisPicker:Spinner
  private val marks=mutableListOf<Button>()
  private class Run(val model:FeedbackProbe,val trace:String) {
    val remaining=AtomicInteger(3)
    val lastLogged=mutableMapOf<String,FeedbackProbe.Sample>()
    val latestTyped=AtomicReference<FeedbackProbe.Sample?>()
  }
  private fun now()=SystemClock.elapsedRealtime()
  private fun dp(value:Int)=(resources.displayMetrics.density*value).toInt()
  override fun onCreate(state:Bundle?) {
    super.onCreate(state)
    if(!StorageAccess.unlocked(this)) { finish();return }
    selected=state?.getString("axis")?.takeIf { it in PositionFeedback.seatAxes } ?: "backrest"
    val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(28),dp(20),dp(28),dp(24));setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun text(value:String,size:Float=18f)=TextView(this).apply { text=value;textSize=size;setTextColor(getColor(R.color.matte_silver));setPadding(0,dp(8),0,dp(8)) }
    fun button(value:String,action:()->Unit)=Button(this).apply { text=value;isAllCaps=false;minHeight=dp(64);layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6);bottomMargin=dp(6) };setOnClickListener { action() } }
    page.addView(text(if(BuildConfig.DEMO) "Seat feedback · simulation" else "Seat feedback",28f))
    page.addView(text("Park in P with the parking brake applied. Record for 10 seconds while using the physical seat controls. This screen sends no vehicle command and saves no preset. Raw SDK readings can be defaults."))
    axisPicker=Spinner(this).apply {
      adapter=ArrayAdapter(this@SeatFeedbackActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Fore/aft","Backrest","Height"))
      minimumHeight=dp(60);setSelection(PositionFeedback.seatAxes.indexOf(selected))
      onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent:AdapterView<*>?) {}
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { selected=PositionFeedback.seatAxes[position] }
      }
    };page.addView(axisPicker)
    start=button("Record feedback · 10 seconds") { begin() };page.addView(start)
    stop=button("Stop recording") { cancel("owner-stop");render() };page.addView(stop)
    val row=LinearLayout(this)
    for((label,kind) in listOf("Mark physical movement" to "physical-movement","Mark physical release" to "physical-release")) {
      val mark=button(label) { current?.takeIf { it.model.open(now()) }?.let { run->
        SeatDiagnostics.event(this,run.trace,"feedback-owner-marker kind=$kind reportedAtMs=${now()} actualActionTime=unknown physicalStop=unverified")
        Toast.makeText(this,"Marker recorded",Toast.LENGTH_SHORT).show()
      } }
      row.addView(mark,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8) });marks+=mark
    };page.addView(row)
    output=text("Ready. Compare fixed SDK getters, the optional typed Car API and typed ADB readings.",17f);page.addView(output)
    page.addView(text(FeedbackProbe.LIVE_GATE))
    page.addView(button(if(BuildConfig.DEMO) "Simulate continuous trial" else "Continuous trial · locked") { simulate() }.apply { isEnabled=BuildConfig.DEMO })
    page.addView(text("Share the private report through ADB Lab after the test. Reading changes and marker times cannot unlock live continuous movement."))
    setContentView(ScrollView(this).apply { addView(page) })
  }
  private fun begin() {
    if(!visible || !busy.compareAndSet(false,true)) { output.text="A feedback read is still running. Wait for it to return.";return }
    if(!SeatMotionState.operations.beginMotion()) { busy.set(false);output.text="A seat load, trial or update is already running.";return }
    val run=Run(FeedbackProbe(selected,now()),"feedback-probe-"+UUID.randomUUID());current=run
    SeatDiagnostics.event(this,run.trace,"feedback-probe start axis=$selected windowMs=${FeedbackProbe.WINDOW_MS} startedAtMs=${now()} readOnly=true continuousEnabled=false sampleFreshness=unverified")
    sdkWorker.execute { try { sdkLoop(run) } catch(e:Throwable) { fail(run,"sdk",e) } finally { done(run) } }
    adbWorker.execute { try { adbLoop(run) } catch(e:Throwable) { fail(run,"typed-adb",e) } finally { done(run) } }
    carWorker.execute { try { carLoop(run) } catch(e:Throwable) { fail(run,"car-property",e) } finally { done(run) } }
    main.post(tick)
  }
  private fun sdkLoop(run:Run) {
    val sdk=SeatFeedbackSdk(applicationContext,run.model.axis,run.trace)
    val park=OemReadBridge(applicationContext)
    while(run.model.open(now())) {
      val cycle=now()
      if(!BuildConfig.DEMO) {
        val state=park.rawPark()
        if(!visible || state.first!=1 || state.second!=3 || !(getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive) {
          run.model.stop();SeatDiagnostics.event(this,run.trace,"feedback-probe stopped=park-brake-or-foreground-unconfirmed readOnly=true");break
        }
      }
      for(route in FeedbackProbe.routes.filter { it.contains('.') }) {
        if(!run.model.open(now()))break
        val began=now()
        val raw=runCatching { if(BuildConfig.DEMO) { Thread.sleep(2);demoValue(run) } else sdk.read(route) }
        accept(run,FeedbackProbe.Sample(route,raw.getOrNull(),began,now(),failure=raw.exceptionOrNull()?.let { SeatDiagnostics.failure(it) }))
      }
      rest(run,cycle,100)
    }
  }
  private fun adbLoop(run:Run) {
    if(BuildConfig.DEMO) {
      while(run.model.open(now())) {
        val cycle=now();Thread.sleep(40)
        accept(run,FeedbackProbe.Sample("typed-adb",demoValue(run),cycle,now(),now()*1_000_000))
        rest(run,cycle,500)
      }
    } else FeedbackOemBridge(applicationContext,run.trace).use { bridge->
      while(run.model.open(now())) {
        val cycle=now();val raw=runCatching { bridge.read().getValue(run.model.axis) }
        accept(run,FeedbackProbe.Sample("typed-adb",raw.getOrNull()?.value,cycle,now(),raw.getOrNull()?.cacheAtNs,raw.exceptionOrNull()?.let { SeatDiagnostics.failure(it) }))
        rest(run,cycle,500)
      }
    }
  }
  private fun carLoop(run:Run) {
    SeatCarPropertyProbe(applicationContext,run.model.axis).use { probe->
      var lastFailure:String?=null
      while(run.model.open(now())) {
        val cycle=now()
        val raw=runCatching { if(BuildConfig.DEMO) PositionFeedback.Sample(demoValue(run),now()*1_000_000) else probe.read() }
        val failure=raw.exceptionOrNull()?.let { SeatDiagnostics.failure(it) }
        accept(run,FeedbackProbe.Sample("car-property",raw.getOrNull()?.value,cycle,now(),raw.getOrNull()?.cacheAtNs,failure))
        // Missing/denied optional APIs do not retry or stop either independent reader.
        if(failure!=null && lastFailure==failure)break
        lastFailure=failure
        rest(run,cycle,200)
      }
    }
  }
  private fun demoValue(run:Run)=50.0+((now()-run.model.startedAt)/500%4)
  private fun rest(run:Run,cycle:Long,period:Long) {
    while(run.model.open(now())) { val remaining=period-(now()-cycle);if(remaining<=0)return;Thread.sleep(minOf(25,remaining)) }
  }
  private fun accept(run:Run,sample:FeedbackProbe.Sample) {
    if(!run.model.accept(sample)) { SeatDiagnostics.event(this,run.trace,"feedback-sample discarded=true route=${sample.route} beganMs=${sample.beganAt} endedMs=${sample.endedAt}");return }
    if(sample.route=="typed-adb")run.latestTyped.set(sample)
    val shouldLog=synchronized(run.lastLogged) {
      val previous=run.lastLogged[sample.route]
      val changed=previous==null || previous.value!=sample.value || previous.failure!=sample.failure || sample.endedAt-previous.endedAt>=500
      if(changed)run.lastLogged[sample.route]=sample
      changed
    }
    if(!shouldLog)return

    val reference=run.latestTyped.get()
    SeatDiagnostics.event(this,run.trace,"feedback-sample axis=${run.model.axis} route=${sample.route} raw=${sample.value} beganMs=${sample.beganAt} endedMs=${sample.endedAt} latencyMs=${sample.latency} cacheAtNs=${sample.cacheAtNs} referenceEndedMs=${reference?.endedAt} difference=${sample.value?.let { value->reference?.value?.let { value-it } }} failure=${sample.failure} freshness=unverified movementEligible=false")
  }
  private fun fail(run:Run,source:String,e:Throwable) {
    SeatDiagnostics.event(this,run.trace,"feedback-probe source=$source failure="+SeatDiagnostics.failure(e))
  }
  private fun done(run:Run) {
    if(run.remaining.decrementAndGet()==0) {
      run.model.stop()
      SeatDiagnostics.event(this,run.trace,"feedback-probe finished readOnly=true continuousEnabled=false summary="+run.model.summary().replace('\n',';'))
      SeatMotionState.operations.endMotion();busy.set(false)
      main.post { if(visible && current===run)render() }
    }
  }
  private fun cancel(reason:String) { current?.let { it.model.stop();SeatDiagnostics.event(this,it.trace,"feedback-probe cancelled=$reason atMs=${now()} queuedRetry=false") } }
  private val tick=object:Runnable { override fun run() { if(!visible)return;render();if(busy.get() || (current?.remaining?.get() ?: 0)>0)main.postDelayed(this,1000) } }
  private fun render() {
    val run=current;val open=run?.model?.open(now())==true
    start.isEnabled=!busy.get();axisPicker.isEnabled=!busy.get();stop.isEnabled=open;marks.forEach { it.isEnabled=open }
    if(run==null && busy.get())output.text="An earlier recording has an in-flight read. Wait for it to return."
    if(run!=null)output.text=(if(open) "Recording. " else if(run.remaining.get()>0) "Stopped. Waiting for the in-flight read. " else "Recording complete. ")+run.model.summary()
  }
  private fun simulate() {
    check(BuildConfig.DEMO && !FeedbackProbe.liveMovementAllowed()) { FeedbackProbe.LIVE_GATE }
    if(busy.get()) { output.text="Stop recording first.";return }
    var at=1_000L;var value=50.0;var held=0;var starts=0;var releases=0
    val port=object:ContinuousSeatTrial.Port {
      override fun clock()=at
      override fun eligible()=true
      override fun latest()=ContinuousSeatTrial.Sample(value,at)
      override fun pause(ms:Long) { at+=ms;if(held!=0)value+=if(held==1) .25 else -.25 }
      override fun signal(direction:Int) { held=direction;if(direction==0)releases++ else starts++ }
    }
    val outcome=runCatching { ContinuousSeatTrial.simulate(port,52.0) }
    output.text=outcome.fold({ "Simulation complete: one start, one release; position $value; elapsed ${at-1000} ms. No vehicle command." },{ "Simulation stopped: ${it.message}" })
    SeatDiagnostics.event(this,"continuous-simulation","starts=$starts releases=$releases final=$value elapsedMs=${at-1000} nativeCommands=0 liveEnabled=false")
  }
  override fun onResume() { super.onResume();visible=true;if(!::output.isInitialized)return;render();if(busy.get())main.post(tick) }
  override fun onPause() { visible=false;cancel("activity-hidden");main.removeCallbacksAndMessages(null);super.onPause() }
  override fun onSaveInstanceState(out:Bundle) { out.putString("axis",selected);super.onSaveInstanceState(out) }
  companion object {
    private val busy=AtomicBoolean(false)
    private val sdkWorker=Executors.newSingleThreadExecutor { r->Thread(r,"seat-feedback-sdk").apply { isDaemon=true } }
    private val carWorker=Executors.newSingleThreadExecutor { r->Thread(r,"seat-feedback-car").apply { isDaemon=true } }
    private val adbWorker=Executors.newSingleThreadExecutor { r->Thread(r,"seat-feedback-adb").apply { isDaemon=true } }
  }
}

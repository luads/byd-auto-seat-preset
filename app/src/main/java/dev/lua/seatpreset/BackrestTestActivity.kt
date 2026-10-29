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

/** One explicitly selected SDK/payload/command, followed by bounded read-only observation. */
class BackrestTestActivity:Activity() {
  private val main=Handler(Looper.getMainLooper())
  @Volatile private var active=false
  @Volatile private var generation=0
  @Volatile private var interventionAt=0L
  private var method=SeatExperimentMethod.SINGLE
  private var source=SeatSdkSource.NATIVE
  private var baseline:Map<String,Double?>?=null
  private var baselineAt=0L
  private var trace="seat-experiment-"+java.util.UUID.randomUUID()
  private lateinit var output:TextView
  private lateinit var access:TextView
  private lateinit var readButton:Button
  private lateinit var setupButton:Button
  private lateinit var minus:Button
  private lateinit var plus:Button
  private lateinit var end:Button
  private lateinit var physical:Button
  private lateinit var restored:Button
  private var observingUntil=0L
  private var hasAttempt=false
  private val selectionControls=mutableListOf<View>()
  private var beforeCommand:Map<String,Double?> = emptyMap()
  private var expectedTarget=Double.NaN
  private val parkBridge by lazy { OemReadBridge(this) }
  private fun experimentBridge()=bridges.getOrPut(source) { BodyworkExperimentBridge(applicationContext,source) }
  private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  override fun onCreate(state:Bundle?) {
    super.onCreate(state)
    if(!StorageAccess.unlocked(this)) { finish();return }
    val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(32),dp(20),dp(32),dp(24));setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun text(value:String,size:Float=18f)=TextView(this).apply { text=value;textSize=size;setTextColor(getColor(R.color.matte_silver));setPadding(0,dp(8),0,dp(8)) }
    fun button(value:String,action:()->Unit)=Button(this).apply {
      text=value;isAllCaps=false;minHeight=dp(76);layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8);bottomMargin=dp(4) };setOnClickListener { action() }
    }
    root.addView(text(if(BuildConfig.DEMO) "Seat experiments · simulation" else "Seat command experiments",28f))
    root.addView(text("Choose one method and SDK, read compatibility, then confirm one ±1 numeric-unit backrest command. This does not guarantee small physical travel. Other axes may react. There is no verified app stop command. Keep native controls ready. Normal preset loading stays blocked."))
    val methods=RadioGroup(this)
    SeatExperimentMethod.values().forEach { item ->
      methods.addView(RadioButton(this).apply {
        selectionControls+=this
        id=View.generateViewId();text=item.label;minHeight=dp(64);setTextColor(getColor(R.color.matte_silver));isChecked=item==method
        setOnCheckedChangeListener { _,checked->if(checked && method!=item) { method=item;reset() } }
      })
    };root.addView(methods)
    root.addView(text("SDK source. Changing it requires a new read; there is no automatic fallback."))
    root.addView(Spinner(this).apply {
      selectionControls+=this
      adapter=ArrayAdapter(this@BackrestTestActivity,android.R.layout.simple_spinner_dropdown_item,SeatSdkSource.values().map { it.label })
      onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { val next=SeatSdkSource.values()[position];if(source!=next) { source=next;reset() } }
        override fun onNothingSelected(parent:AdapterView<*>?) {}
      }
      minimumHeight=dp(64)
    })
    access=text("");root.addView(access)
    setupButton=button("Set up experiment access") {
      AlertDialog.Builder(this).setTitle("Grant experiment access?")
        .setMessage("Grant this app read access and BODYWORK_SET through its local setup connection. This is separate from read-only setup. It enables access only; it does not move anything, enable automatic recall or change startup recovery.")
        .setNegativeButton("Cancel",null).setPositiveButton("Set up") { _,_->setupAccess() }.show()
    };root.addView(setupButton)
    output=text("Read compatibility first. Missing, zero, ambiguous and sentinel geometry values cannot be movement targets.",20f);root.addView(output)
    readButton=button("Read current position") { prepare() };root.addView(readButton)
    val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
    minus=button("Try −1 with selected method") { confirm(-1) };plus=button("Try +1 with selected method") { confirm(1) }
    actions.addView(minus,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(12) });actions.addView(plus,LinearLayout.LayoutParams(0,-2,1f));root.addView(actions)
    physical=button("I used the physical control") { markIntervention("physical-control") };restored=button("I restored the native preset") { markIntervention("native-preset-restored") }
    root.addView(physical);root.addView(restored)
    end=button("End observation") { stopObserving("owner ended observation") };root.addView(end)
    root.addView(button("Back") { finish() })
    setContentView(ScrollView(this).apply { addView(root) });showAccess();updateButtons()
  }
  private fun enabled()=SeatControl.enabled(this)
  private fun showAccess() {
    if(!::access.isInitialized) return
    access.text=if(BuildConfig.DEMO) "Simulation only. No OEM command or permission grant."
      else "Missing access: "+(VehicleReadSetup.missing(this)+if(checkSelfPermission(SeatExperimentAccess.WRITE)!=android.content.pm.PackageManager.PERMISSION_GRANTED) listOf(SeatExperimentAccess.WRITE) else emptyList()).joinToString { it.substringAfter("BYDAUTO_") }.ifBlank { "none; SDK compatibility still unverified" }
  }
  private fun updateButtons() {
    if(!::readButton.isInitialized) return
    readButton.isEnabled=active && !busy.get() && observingUntil==0L
    val ready=active && !busy.get() && enabled() && baseline!=null && method!=SeatExperimentMethod.LEGACY && observingUntil==0L
    minus.isEnabled=ready;plus.isEnabled=ready;setupButton.isEnabled=active && !busy.get() && observingUntil==0L
    selectionControls.forEach { it.isEnabled=!busy.get() && observingUntil==0L }
    end.visibility=if(observingUntil>0) View.VISIBLE else View.GONE
    physical.isEnabled=hasAttempt;restored.isEnabled=hasAttempt
  }
  private fun reset() {
    generation++;baseline=null;observingUntil=0;hasAttempt=false;interventionAt=0;main.removeCallbacksAndMessages(null)
    if(::output.isInitialized) output.text=if(method==SeatExperimentMethod.LEGACY) "Way A is retired: coupled movement was reported. Named bodywork aliases reach the same integer properties and are not offered as new methods."
      else "${method.label} · ${source.label}. Read again before testing."
    updateButtons()
  }
  private fun park():VehicleSnapshot {
    if(BuildConfig.DEMO) return DemoVehicle(this).snapshot()
    val raw=parkBridge.rawPark();val awake=(getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
    return VehicleSnapshot(if(raw.first==1 && awake) Gear.P else null,if(raw.second==3) true else null,null,SystemClock.elapsedRealtime())
  }
  private fun readings(detailed:Boolean=true):Map<String,Double?> {
    if(BuildConfig.DEMO) return demoGeometry.mapKeys { "geometry.${it.key}" }
    val values=linkedMapOf<String,Double?>()
    parkBridge.percentageSeatCandidates().forEach { (axis,value)->values["setting.$axis"]=value }
    parkBridge.horizontalProperty()?.let { (value,evidence)->values["property.horizontal"]=value;SeatDiagnostics.event(this,trace,"observation axis=horizontal value=$value ${evidence.describe()}") }
    val geometry=experimentBridge().read(trace,detailed=detailed)
    geometry.forEach { (axis,value)->values["geometry.$axis"]=value }
    return values
  }
  private fun geometry(values:Map<String,Double?>)=SeatExperiment.axes.associateWith { values["geometry.$it"] }
  private fun log(stage:String,values:Map<String,Double?>) {
    SeatDiagnostics.event(this,trace,"stage=$stage method=$method sdk=$source sampledAtMs=${SystemClock.elapsedRealtime()} values=$values ownerInterventionAtMs=$interventionAt attribution=${if(interventionAt>0) "owner-intervened" else "unverified"}")
  }
  private fun prepare() {
    if(!active) return
    if(!busy.compareAndSet(false,true)) { output.text="An earlier call is still running. No second call was queued.";return }
    baseline=null;trace="seat-experiment-"+java.util.UUID.randomUUID();interventionAt=0;hasAttempt=false
    val token=++generation;val selected=method
    output.text="Reading compatibility for ${method.label} · ${source.label}…";updateButtons();deadline(token,"read")
    worker.execute {
      val result=runCatching {
        check(enabled()) { "Enable Allow seat movement in Settings first" }
        check(selected!=SeatExperimentMethod.LEGACY) { "Way A is retired after coupled movement" }
        RecallPolicy.blockReason(park(),SystemClock.elapsedRealtime())?.let { error(it) }
        val a=readings();Thread.sleep(200);val b=readings();log("before-first",a);log("before-second",b)
        val first=geometry(a);val second=geometry(b)
        val payload=SeatExperiment.payload(selected,second,1)
        if(!BuildConfig.DEMO) experimentBridge().requireWritableBackend()
        else check(source==SeatSdkSource.NATIVE) { "Collector comparison is read-only" }
        check(payload.keys.all { first[it]==second[it] }) { "Geometry changed during the read. Stop adjusting and read again." }
        RecallPolicy.blockReason(park(),SystemClock.elapsedRealtime())?.let { error(it) }
        second
      }.onFailure { SeatDiagnostics.event(this,trace,"prepareRejected method=$selected sdk=$source cause="+SeatDiagnostics.failure(it)) }
      busy.set(false)
      main.post { if(active && token==generation && !isFinishing) {
        result.fold({ baseline=it;baselineAt=SystemClock.elapsedRealtime();output.text="${method.label} · ${source.label}\nGeneric geometry: $it\nCompatibility reads passed. Units, write behavior and physical travel remain unverified. Choose a numeric step, then confirm." },
          { output.text="Not ready: ${it.message}" });updateButtons()
      } }
    }
  }
  private fun deadline(token:Int,kind:String) {
    main.postDelayed({ if(active && token==generation && busy.get()) {
      generation++;baseline=null;output.text="$kind timed out. Outcome may be unknown. No retry or second call was queued. Wait or restart the app.";updateButtons()
    } },20_000)
  }
  private fun confirm(delta:Int) {
    val from=baseline ?: return
    if(!active || busy.get()) return
    if(SystemClock.elapsedRealtime()-baselineAt>30_000) { baseline=null;output.text="Read the position again before testing.";updateButtons();return }
    val values=SeatExperiment.payload(method,from,delta)
    val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(8),dp(24),dp(8)) }
    content.addView(TextView(this).apply { text="${method.label}\nSDK: ${source.label}\nBackrest ${from["backrest"]} → ${values["backrest"]}\nPayload: floatArrayValue, ${values.size} seat axis/axes. Unchanged fields are preserved in Way C. Mirrors and steering are excluded.\n\nUnits, travel and coupling are unverified. The app cannot stop an in-flight command. One attempt only, with ten seconds of readback.";textSize=18f })
    val acknowledged=CheckBox(this).apply { text="I am in P, the parking brake is applied, the seat is clear, and native controls are within reach.";minHeight=dp(76) };content.addView(acknowledged)
    val token=generation
    val dialog=AlertDialog.Builder(this).setTitle(if(BuildConfig.DEMO) "Simulate selected experiment?" else "Run selected experiment once?")
      .setView(object:ScrollView(this) { override fun onMeasure(w:Int,h:Int) { super.onMeasure(w,View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels*.45f).toInt(),View.MeasureSpec.AT_MOST)) } }.apply { addView(content) })
      .setNegativeButton("Cancel",null).setPositiveButton(if(BuildConfig.DEMO) "Simulate" else "Move once") { _,_->if(acknowledged.isChecked && active && token==generation) dispatch(from,delta,token) }.create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false;acknowledged.setOnCheckedChangeListener { _,on->dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=on } };dialog.show()
  }
  private fun dispatch(from:Map<String,Double?>,delta:Int,token:Int) {
    if(!active || token!=generation || !busy.compareAndSet(false,true)) return
    val commandToken=++generation;val requestedAt=SystemClock.elapsedRealtime();baseline=null;beforeCommand=emptyMap();hasAttempt=true
    val selected=method;val selectedSource=source;val attempted=AtomicBoolean(false)
    output.text="Final parked-state, geometry and request checks. One SDK call at most…";updateButtons();deadline(commandToken,"command")
    worker.execute {
      val result=runCatching {
        val permit=SeatExperimentPermit.issue(selected,selectedSource,from,delta,requestedAt)
        expectedTarget=checkNotNull(permit.values["backrest"]).toDouble()
        SeatDiagnostics.event(this,trace,"ownerConfirmed=true "+SeatExperiment.describe(selected,selectedSource,permit.values)+" requestedAtMs=$requestedAt noAutomaticFallback=true")
        beforeCommand=readings().toMap();log("before-command",beforeCommand)
        val code=if(BuildConfig.DEMO) {
          val values=permit.consume(SystemClock.elapsedRealtime(),demoGeometry,park(),active && commandToken==generation,enabled(),source)
          attempted.set(true);demoGeometry=demoGeometry+values.mapValues { it.value.toDouble() };0
        } else experimentBridge().send(trace,permit,::park,{ active && commandToken==generation && source==selectedSource },{ attempted.set(true) })
        SeatDiagnostics.event(this,trace,"setterReturned method=$selected sdk=$selectedSource rawResult=$code commandsAttempted=1 physicalCompletion=unverified")
        check(code==0) { "SDK returned $code. No fallback will run." }
      }.onFailure { SeatDiagnostics.event(this,trace,"commandFailed method=$selected sdk=$selectedSource attempted=${attempted.get()} cause="+SeatDiagnostics.failure(it)+" noRetry=true") }
      busy.set(false)
      main.post { if(active && commandToken==generation && !isFinishing) {
        output.text=result.fold({ "SDK returned zero; physical completion is unverified. Observing available axes…" },{ "${if(attempted.get()) "Attempt outcome unverified" else "Rejected before dispatch"}: ${it.message}. No retry." })
        observingUntil=SystemClock.elapsedRealtime()+10_000;updateButtons();observe(commandToken)
      } }
    }
  }
  private fun observe(token:Int) {
    if(!active || token!=generation || observingUntil==0L) return
    if(SystemClock.elapsedRealtime()>=observingUntil) { stopObserving("observation window ended");return }
    if(!busy.compareAndSet(false,true)) { main.postDelayed({ observe(token) },500);return }
    worker.execute {
      val result=runCatching { readings(detailed=false) }.onSuccess { log("after",it);SeatDiagnostics.event(this,trace,BackrestTestReadback.describe(beforeCommand,it,expectedTarget,"geometry.backrest")+" ownerInterventionAtMs=$interventionAt") }
        .onFailure { SeatDiagnostics.event(this,trace,"observationFailed="+SeatDiagnostics.failure(it)) }
      busy.set(false)
      main.post {
        if(active && !isFinishing) updateButtons()
        if(active && token==generation && observingUntil>0) {
        result.onSuccess { output.text="${method.label} · ${source.label}\n$it\n"+BackrestTestReadback.describe(beforeCommand,it,expectedTarget,"geometry.backrest")+if(interventionAt>0) "\nOwner intervention marked; these readings cannot be attributed solely to the app command." else "\nUse the markers below if you intervene. End observation does not stop the seat." }
        main.postDelayed({ observe(token) },500)
      } }
    }
    main.postDelayed({ if(active && token==generation && observingUntil>0 && SystemClock.elapsedRealtime()>=observingUntil) stopObserving("observation deadline reached") },(observingUntil-SystemClock.elapsedRealtime()).coerceAtLeast(1))
  }
  private fun markIntervention(kind:String) {
    interventionAt=SystemClock.elapsedRealtime();val token=++generation;baseline=null;main.removeCallbacksAndMessages(null)
    SeatDiagnostics.event(this,trace,"ownerIntervention=$kind reportedAtMs=$interventionAt timingOfPhysicalAction=unverified earlierSamplesMayIncludeIntervention=true")
    output.text="Owner intervention marked. Earlier samples may also include manual movement. This marker sends no vehicle command."
    observingUntil=SystemClock.elapsedRealtime()+10_000;updateButtons();observe(token)
  }
  private fun stopObserving(reason:String) {
    observingUntil=0;baseline=null;main.removeCallbacksAndMessages(null)
    SeatDiagnostics.event(this,trace,"observationEnded reason=$reason physicalCompletion=unverified ownerInterventionAtMs=$interventionAt")
    output.text="Observation ended. Review the private report and physical position. No completion is claimed.";updateButtons()
  }
  private fun setupAccess() {
    if(!active || observingUntil!=0L) return
    if(BuildConfig.DEMO) { output.text="Simulation needs no OEM permission grant.";return }
    if(!busy.compareAndSet(false,true)) return
    baseline=null;val token=++generation;output.text="Setting up experiment permissions…";updateButtons()
    worker.execute {
      val text=runCatching { SeatExperimentAccess.enable(this) }.getOrElse { SeatDiagnostics.event(this,trace,"accessFailed="+SeatDiagnostics.failure(it));"Setup failed: ${it.message}" }
      busy.set(false);main.post { if(active && token==generation && !isFinishing) { output.text=text;showAccess();updateButtons() } }
    }
  }
  override fun onResume() { super.onResume();active=true;showAccess();updateButtons() }
  override fun onPause() { active=false;generation++;baseline=null;observingUntil=0;main.removeCallbacksAndMessages(null);super.onPause() }
  companion object {
    private val busy=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor { task->Thread(task,"owner-seat-experiment").apply { isDaemon=true } }
    private val bridges=mutableMapOf<SeatSdkSource,BodyworkExperimentBridge>()
    @Volatile private var demoGeometry:Map<String,Double?> = SeatExperiment.axes.associateWith { 50.0 }
  }
}

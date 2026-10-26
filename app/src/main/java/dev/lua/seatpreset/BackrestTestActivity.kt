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

/** Foreground owner-operated experiment. No saved preset or normal recall can enter this path. */
class BackrestTestActivity:Activity() {
  private val main=Handler(Looper.getMainLooper())
  @Volatile private var active=false
  @Volatile private var generation=0
  private var baseline:Double?=null
  private var baselineAt=0L
  private var trace="backrest-test-"+java.util.UUID.randomUUID()
  private lateinit var output:TextView
  private lateinit var readButton:Button
  private lateinit var minus:Button
  private lateinit var plus:Button
  private lateinit var end:Button
  private var observingUntil=0L
  private var beforeCommand:Map<String,Double?> = emptyMap()
  private var expectedTarget=Double.NaN
  private val bridge by lazy { OemReadBridge(this) }
  override fun onCreate(state:Bundle?) {
    super.onCreate(state)
    if(!StorageAccess.unlocked(this)) { finish();return }
    val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(32),dp(20),dp(32),dp(24));setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun text(value:String,size:Float=18f)=TextView(this).apply { text=value;textSize=size;setTextColor(getColor(R.color.matte_silver));setPadding(0,dp(8),0,dp(8)) }
    fun button(value:String,action:()->Unit)=Button(this).apply {
      text=value;isAllCaps=false;minHeight=dp(76)
      layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8);bottomMargin=dp(4) }
      setOnClickListener { action() }
    }
    root.addView(text(if(BuildConfig.DEMO) "Backrest test · simulation" else "Backrest movement test",28f))
    root.addView(text("One owner-confirmed command on the driver's backrest, area 1. A one-unit numeric change does not guarantee small physical travel. The command meaning and effects on other axes remain unverified. This app has no verified seat-stop command. Keep native controls within reach and the seat clear."))
    root.addView(text("Normal preset loading remains blocked. No presets are changed. Requires Allow seat movement, reported P and parking brake, plus your physical confirmation. No foot-brake input is required."))
    output=text("Read the current position first. Only available observation routes are monitored; stable values do not prove ECU-cache freshness.",20f);root.addView(output)
    readButton=button("Read current position") { prepare() };root.addView(readButton)
    val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
    minus=button("Backrest −1 unit") { confirm(-1) };plus=button("Backrest +1 unit") { confirm(1) }
    actions.addView(minus,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(12) });actions.addView(plus,LinearLayout.LayoutParams(0,-2,1f))
    root.addView(actions)
    end=button("End observation") { stopObserving("owner ended observation") };root.addView(end)
    root.addView(button("Back") { finish() })
    setContentView(ScrollView(this).apply { addView(root) });updateButtons()
  }
  private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  private fun enabled()=SeatControl.enabled(this)
  private fun updateButtons() {
    if(!::readButton.isInitialized) return
    readButton.isEnabled=active && observingUntil==0L
    val available=active && !busy.get() && enabled() && baseline!=null && observingUntil==0L
    minus.isEnabled=available;plus.isEnabled=available;end.visibility=if(observingUntil>0) View.VISIBLE else View.GONE
  }
  private fun park():VehicleSnapshot {
    if(BuildConfig.DEMO) return DemoVehicle(this).snapshot()
    val raw=bridge.rawPark()
    return VehicleSnapshot(if(raw.first==1) Gear.P else null,if(raw.second==3) true else null,null,SystemClock.elapsedRealtime())
  }
  private fun readings():Map<String,Double?> {
    val values=linkedMapOf<String,Double?>()
    if(BuildConfig.DEMO) return mapOf("setting.backrest" to demoBackrest,"setting.height" to 40.0,"property.horizontal" to 40.0)
    bridge.percentageSeatCandidates().forEach { (axis,value)->values["setting.$axis"]=value }
    bridge.horizontalProperty()?.let { (value,evidence)->
      values["property.horizontal"]=value
      SeatDiagnostics.event(this,trace,"observation axis=horizontal value=$value ${evidence.describe()}")
    }
    runCatching { bridge.absoluteSeatCandidates() }.fold({ raw->raw.forEach { (route,value)->values["bodywork.$route"]=value } },
      { SeatDiagnostics.event(this,trace,"bodyworkUnavailable="+SeatDiagnostics.failure(it)) })
    return values
  }
  private fun log(stage:String,values:Map<String,Double?>) {
    SeatDiagnostics.event(this,trace,"stage=$stage sampledAtMs=${SystemClock.elapsedRealtime()} values=$values cacheFreshness=unverified")
  }
  private fun prepare() {
    if(!active) return
    if(!busy.compareAndSet(false,true)) { output.text="An earlier vehicle call is still running. No second read was started. Wait or restart the app.";return }
    baseline=null;trace="backrest-test-"+java.util.UUID.randomUUID();val token=++generation
    output.text="Reading the seat and parked state…";updateButtons()
    main.postDelayed({ if(active && token==generation && busy.get()) {
      generation++;baseline=null;output.text="Read timed out. Its result will be discarded. Wait for the earlier call to return or restart the app.";updateButtons()
    } },20_000)
    worker.execute {
      val result=runCatching {
        check(enabled()) { "Enable Allow seat movement in Settings first" }
        if(!BuildConfig.DEMO) {
          val missing=VehicleReadSetup.missing(this).filter { it.contains("SETTING_") || it.contains("GEARBOX_") }
          check(missing.isEmpty()) { "Set up vehicle readings first. Missing: "+missing.joinToString { it.substringAfter("BYDAUTO_") } }
        }
        RecallPolicy.blockReason(park(),SystemClock.elapsedRealtime())?.let { error(it) }
        val a=readings();Thread.sleep(200);val b=readings();log("before-first",a);log("before-second",b)
        val backrest=checkNotNull(b["setting.backrest"]) { "Backrest reading unavailable" }
        check(a["setting.backrest"]==backrest) { "Backrest reading changed. Wait until stopped and read again." }
        BackrestTestPermit.issue(backrest,1,SystemClock.elapsedRealtime())
        RecallPolicy.blockReason(park(),SystemClock.elapsedRealtime())?.let { error(it) }
        backrest to b
      }.onFailure { SeatDiagnostics.event(this,trace,"prepareFailed="+SeatDiagnostics.failure(it)) }
      busy.set(false)
      main.post { if(active && token==generation && !isFinishing) {
        result.fold({ (raw,values)->baseline=raw;baselineAt=SystemClock.elapsedRealtime();output.text="Backrest reading: $raw (unverified units).\n$values\nChoose a one-unit test, then review and confirm. No command sent yet." },
          { output.text=it.message ?: "Read unavailable" });updateButtons()
      } }
    }
  }
  private fun confirm(delta:Int) {
    val from=baseline ?: return
    if(!active || busy.get()) return
    if(SystemClock.elapsedRealtime()-baselineAt>30_000) { baseline=null;output.text="Read the current position again before testing.";updateButtons();return }
    val target=from+delta
    val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(8),dp(24),dp(8)) }
    content.addView(TextView(this).apply { text="One command: driver backrest $from → $target.\n\nThe numeric units and physical travel are unverified. Other axes may react. The app cannot promise a small movement or stop an in-flight command. It will observe available readings for 10 seconds and will not retry the command.";textSize=18f })
    val check=CheckBox(this).apply { text="I am in P, the parking brake is applied, the seat is clear, and native controls are within reach.";minHeight=dp(76) };content.addView(check)
    val token=generation
    val dialog=AlertDialog.Builder(this).setTitle(if(BuildConfig.DEMO) "Simulate one backrest command?" else "Move the backrest once?")
      .setView(object:ScrollView(this) {
        override fun onMeasure(width:Int,height:Int) {
          val cap=(resources.displayMetrics.heightPixels*.45f).toInt()
          super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST))
        }
      }.apply { addView(content) }).setNegativeButton("Cancel",null)
      .setPositiveButton(if(BuildConfig.DEMO) "Simulate" else "Move once") { _,_-> if(check.isChecked && active && token==generation) dispatch(from,delta,token) }.create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
      check.setOnCheckedChangeListener { _,on->dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=on }
    };dialog.show()
  }
  private fun dispatch(from:Double,delta:Int,token:Int) {
    if(!active || token!=generation || !busy.compareAndSet(false,true)) return
    generation++
    val commandToken=generation
    val requestedAt=SystemClock.elapsedRealtime();baseline=null
    beforeCommand=emptyMap();expectedTarget=from+delta
    val attempted=AtomicBoolean(false)
    output.text="Checking P, parking brake and backrest again. One command at most…";updateButtons()
    main.postDelayed({ if(active && commandToken==generation && busy.get()) {
      generation++;output.text="The command call timed out. Its outcome is unknown. No retry will be sent. Use native controls to inspect the physical position.";updateButtons()
    } },20_000)
    worker.execute {
      val result=runCatching {
        val permit=BackrestTestPermit.issue(from,delta,requestedAt)
        SeatDiagnostics.event(this,trace,"ownerConfirmed=true experimental=true axis=backrest from=${permit.from} target=${permit.target} driverArea=1 requestedAtMs=$requestedAt noAutomaticRetry=true")
        beforeCommand=readings().toMap();expectedTarget=permit.target.toDouble();log("before-command",beforeCommand)
        if(BuildConfig.DEMO) {
          val target=permit.consume(SystemClock.elapsedRealtime(),demoBackrest,park(),active && commandToken==generation,enabled())
          attempted.set(true);demoBackrest=target.toDouble()
        } else bridge.sendBackrestTest(permit,{ active && commandToken==generation },{ attempted.set(true) })
        SeatDiagnostics.event(this,trace,"setterReturned axis=backrest physicalCompletion=unverified commandsAttempted=1")
      }.onFailure { SeatDiagnostics.event(this,trace,"commandFailed attempted=${attempted.get()} cause="+SeatDiagnostics.failure(it)+" noRetry=true") }
      busy.set(false)
      main.post { if(active && commandToken==generation && !isFinishing) {
        output.text=result.fold({ "Command returned. Physical completion is unverified. Observing available axes for 10 seconds…" },
          { (if(attempted.get()) "Command attempted; outcome unverified: " else "Rejected before dispatch: ")+"${it.message}. No retry. Observing available readings…" })
        observingUntil=SystemClock.elapsedRealtime()+10_000;updateButtons();observe(commandToken)
      } }
    }
  }
  private fun observe(token:Int) {
    if(!active || token!=generation || observingUntil==0L) return
    if(SystemClock.elapsedRealtime()>=observingUntil) { stopObserving("observation window ended");return }
    if(!busy.compareAndSet(false,true)) { main.postDelayed({ observe(token) },500);return }
    worker.execute {
      val result=runCatching { readings() }.onSuccess {
        log("after",it)
        SeatDiagnostics.event(this,trace,BackrestTestReadback.describe(beforeCommand,it,expectedTarget))
      }
        .onFailure { SeatDiagnostics.event(this,trace,"observationFailed="+SeatDiagnostics.failure(it)) }
      busy.set(false)
      main.post { if(active && token==generation && observingUntil>0) {
        result.onSuccess { output.text="Latest observations (unverified cache freshness):\n$it\n\n"+BackrestTestReadback.describe(beforeCommand,it,expectedTarget)+"\n\nEnd observation does not stop the seat." }
        main.postDelayed({ observe(token) },500)
      } }
    }
    // A blocked OEM getter must not keep the UI waiting or queue more reads.
    main.postDelayed({ if(active && token==generation && observingUntil>0 && SystemClock.elapsedRealtime()>=observingUntil) stopObserving("observation deadline reached") },(observingUntil-SystemClock.elapsedRealtime()).coerceAtLeast(1))
  }
  private fun stopObserving(reason:String) {
    observingUntil=0;baseline=null;main.removeCallbacksAndMessages(null)
    SeatDiagnostics.event(this,trace,"observationEnded reason=$reason commandRetry=false physicalCompletion=unverified")
    output.text="Observation ended. Review the private report and physical position. No movement completion is claimed.";updateButtons()
  }
  override fun onResume() { super.onResume();active=true;updateButtons() }
  override fun onPause() { active=false;generation++;baseline=null;observingUntil=0;main.removeCallbacksAndMessages(null);super.onPause() }
  companion object {
    private val busy=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor { task->Thread(task,"owner-backrest-test").apply { isDaemon=true } }
    @Volatile private var demoBackrest=50.0
  }
}

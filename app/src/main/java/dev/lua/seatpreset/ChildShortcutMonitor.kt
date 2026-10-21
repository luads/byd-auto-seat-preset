package dev.lua.seatpreset

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in reader in the existing accessibility service. P/awake only; requests share the recall controller. */
internal class ChildShortcutMonitor(private val context: Context) {
  private val main=Handler(Looper.getMainLooper())
  private val worker=Executors.newSingleThreadExecutor()
  private val reading=AtomicBoolean(false)
  private val recognizer=ChildLockRecognizer()
  private var active=false
  private var generation=0
  private var bridge: OemReadBridge?=null
  private var lastRequest=-10_000L
  fun refresh() {
    val allowed=runCatching {
      StorageAccess.unlocked(context) && SeatControl.enabled(context) && ChildBindings(context).enabled &&
        (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive &&
        GearModeRead.fromJson(android.provider.Settings.Global.getString(context.contentResolver,GearModeRead.SETTING))==1
    }.getOrDefault(false)
    if(!allowed) { stop();return }
    if(active) return
    active=true;generation++;recognizer.reset();tick(generation)
  }
  private fun tick(token:Int) {
    if(!active || token!=generation) return
    if(!reading.compareAndSet(false,true)) { main.postDelayed({ tick(token) },100);return }
    worker.execute {
      val result=runCatching {
        val sdk=bridge ?: OemReadBridge(context).also { bridge=it }
        val pair=sdk.children()
        val parked=GearModeRead.fromJson(android.provider.Settings.Global.getString(context.contentResolver,GearModeRead.SETTING))==1
        Triple(pair.first,pair.second,parked)
      }.getOrNull()
      val at=SystemClock.elapsedRealtime();reading.set(false)
      main.post {
        if(!active || token!=generation) return@post
        val side=if(result?.third==true) recognizer.sample(ChildSample(at,result.first,result.second)) else { recognizer.reset();null }
        if(side!=null && at-lastRequest>=1500) {
          runCatching {
            val mapped=ChildBindings(context).preset(side)
            if(mapped?.position!=null && ChildBindings(context).enabled) {
              lastRequest=at
              PrivateDiagnostics.record(context,"child shortcut side=$side requests mapped preset")
              Vehicle.requestRecall(context,mapped) { result -> android.widget.Toast.makeText(context,result.message,android.widget.Toast.LENGTH_LONG).show() }
            }
          }
        }
        refresh()
        if(active && token==generation) main.postDelayed({ tick(token) },100)
      }
    }
  }
  fun stop() { active=false;generation++;recognizer.reset();main.removeCallbacksAndMessages(null) }
  fun destroy() { stop();worker.shutdownNow() }
}

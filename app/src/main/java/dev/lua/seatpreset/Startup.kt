package dev.lua.seatpreset

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class SeatApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Startup.start(this)
    runCatching { registerReceiver(object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) { Startup.retry(context) }
    }, IntentFilter(Intent.ACTION_USER_UNLOCKED)) }
  }
}

@android.annotation.SuppressLint("ApplySharedPref")
object Startup {
  private val worker = Executors.newSingleThreadExecutor()
  private val gate = RetryGate()
  private val lock = Any()
  private val choice = RecoveryChoice()
  @Volatile internal var times = StartupTimes(SystemClock.elapsedRealtime())
  @Volatile var tcpAvailable: Boolean? = null; private set
  @Volatile var adbAuthenticated: Boolean? = null; internal set
  // The live adapter still has no validated OEM getter binding.
  val oemGetterReady: Boolean? get() = null
  private val reads = DeferredReadWork()
  private var widgetPending = true
  private fun boot(context: Context) = context.createDeviceProtectedStorageContext().getSharedPreferences("startup-bootstrap", Context.MODE_PRIVATE)
  private fun policy(context: Context): Boolean? = runCatching { choice.read {
    val p = boot(context); if (p.contains("reopen-adb")) p.getBoolean("reopen-adb", false) else null
  } }.getOrNull()
  private fun persist(context: Context, value: Boolean): Boolean {
    choice.save(value) { boot(context).edit().putBoolean("reopen-adb", it).commit() }
    return true
  }
  private fun migrate(context: Context) = synchronized(lock) {
    choice.retry { boot(context).edit().putBoolean("reopen-adb", it).commit() }
    StartupPolicy.migrate(StorageAccess.unlocked(context), {
      val p = boot(context)
      if (p.contains("reopen-adb")) p.getBoolean("reopen-adb", false) else null
    }, {
      val p = StorageAccess.prefs(context, "startup")
      if (p.contains("reopen-adb")) p.getBoolean("reopen-adb", false) else null
    }, { persist(context, it) })
  }
  fun enabled(context: Context) = policy(context) == true
  fun setEnabled(context: Context, enabled: Boolean) = synchronized(lock) {
    // DP is authoritative, including opt-out. Never overwrite it from the legacy CE mirror.
    persist(context, enabled)
    retry(context)
  }
  fun canWrite(context: Context) = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
  private fun port(): Boolean? = try {
    Socket().use { it.connect(InetSocketAddress("127.0.0.1", 5555), 400); true }
  } catch (_: java.net.ConnectException) { false } catch (_: Exception) { null }
  private fun recover(context: Context): String = synchronized(lock) {
    val armed = policy(context)
    if (armed != true) return if (armed == false) "ADB startup disabled" else "ADB startup choice unknown"
    if (!canWrite(context)) return "ADB startup needs the one-time permission grant"
    val open = port(); tcpAvailable = open
    if (open != false) return if (open == true) "ADB port already open" else "ADB port availability unknown"
    val adb = runCatching { Settings.Global.getString(context.contentResolver, "adb_enabled") }.getOrNull()
    val wifi = runCatching { Settings.Global.getString(context.contentResolver, "adb_wifi_enabled") }.getOrNull()
    if (!StartupPolicy.needsReassert(armed, open, true, adb, wifi)) return "ADB settings not known to be cleared"
    return listOf("adb_enabled" to adb, "adb_wifi_enabled" to wifi).filter { it.second == "0" }.joinToString(" · ") { (key, _) ->
      check(Settings.Global.putInt(context.contentResolver, key, 1)) { "Setting persistence failed" }
      check(Settings.Global.getString(context.contentResolver, key) == "1") { "Setting verification failed" }
      "$key=0→1"
    }
  }
  // UI callers never do a socket connect on the main thread.
  fun reopen(context: Context): String { retry(context); return "Startup check requested" }
  fun start(context: Context) {
    times = StartupTimes(SystemClock.elapsedRealtime())
    WidgetDiagnostics.record(context, "process started up=${times.processStartedAtMs}")
    reads.offer("channel-ready") { WidgetDiagnostics.record(context, "ADB authentication verified") }
    retry(context)
    val handler = Handler(Looper.getMainLooper())
    val deadline = times.processStartedAtMs + 120_000L
    val watch = object : Runnable {
      override fun run() {
        if (SystemClock.elapsedRealtime() >= deadline) return
        retry(context); handler.postDelayed(this, 5_000)
      }
    }
    handler.postDelayed(watch, 5_000)
  }
  fun retry(context: Context) {
    if (!gate.request()) return
    val app = context.applicationContext
    worker.execute {
      do {
        fun safe(action: String, block: () -> Unit) {
          runCatching(block).onFailure { WidgetDiagnostics.record(app, "$action deferred ${it.javaClass.simpleName}") }
        }
        safe("policy") { migrate(app) }
        safe("recovery") {
          val status = recover(app)
          if (StorageAccess.unlocked(app)) {
            val store = PresetStore(app)
            if (store.startup != status) store.startup = status
          }
        }
        if (StorageAccess.unlocked(app)) {
          safe("storage") {
            // A successful CE read, not merely UserManager, marks storage-ready.
            StorageAccess.prefs(app, "seat-presets-v1").all
            if (times.storageReadyAtMs == null) {
              times.storageReady(SystemClock.elapsedRealtime())
              WidgetDiagnostics.record(app, "storage ready up=${times.storageReadyAtMs}")
            }
          }
          safe("update-result") { SilentUpdater.resolve(app) }
          safe("updates") { SilentUpdater.onNaturalTick(app) }
          safe("public-log") { WidgetDiagnostics.flush(app) }
          safe("private-log") { PrivateDiagnostics.flush(app) }
          safe("authentication") {
            adbAuthenticated = null
            tcpAvailable = port()
            if (tcpAvailable == true) adbAuthenticated = PresetChannel.authenticateExisting(app)
            else adbAuthenticated = null
            reads.retry(true, adbAuthenticated)
          }
          safe("widget") {
            if (widgetPending) { PresetWidget.refresh(app); widgetPending = false }
          }
        }
      } while (gate.finish())
    }
  }
}

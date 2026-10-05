package dev.lua.seatpreset

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.provider.Settings
import android.widget.Toast

/** Window identity only. No node retrieval, event text, key interception or polling. */
class HomeVisibilityService : AccessibilityService() {
  private var panel: View? = null
  private var homeVisible = false
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private val expiry = Runnable { hide(true) }
  private var fading = false
  private val demoChanges = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshVehicle() }
  private lateinit var windows: WindowManager
  private var homePackage: String? = null
  private var homeClass: String? = null
  private val screenOff = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) { hide() }
  }
  override fun onServiceConnected() {
    super.onServiceConnected(); reference = java.lang.ref.WeakReference(this)
    windows = getSystemService(WINDOW_SERVICE) as WindowManager
    val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo
    homePackage = home?.packageName; homeClass = home?.name
    registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    WidgetDiagnostics.record(this, "home observer connected resolved=${home != null}")
    runCatching { if (BuildConfig.DEMO) StorageAccess.prefs(this, "demo-vehicle").registerOnSharedPreferenceChangeListener(demoChanges) }
    hide()
  }
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    Startup.retry(this)
    runCatching {
      StorageAccess.requireUnlocked(this)
      val pkg = event.packageName?.toString(); val cls = event.className?.toString()
      // Our non-focusable overlay can announce its root without changing the foreground activity.
      if (panel != null && pkg == packageName && cls == "android.widget.LinearLayout") return
      val home = HomePanelPolicy.isHome(homePackage, homeClass, pkg, cls)
      homeVisible = home
      if (home) refreshVehicle() else hide()
    }.onFailure { hide(); WidgetDiagnostics.record(this, "home observer failed ${it.javaClass.simpleName}") }
  }
  // A validated live adapter must deliver new samples here. Never poll the car for display.
  fun refreshVehicle() {
    runCatching {
    StorageAccess.requireUnlocked(this)
    val state = Vehicle.adapter(this).snapshot()
    val settings = HomePanelSettings(this)
    handler.removeCallbacks(expiry)
    if (Settings.canDrawOverlays(this) && PanelVisibility.allowed(homeVisible, settings.enabled, state,
        android.os.SystemClock.elapsedRealtime(), settings.parkOnly)) {
      show()
      // Demo Fresh represents an ongoing simulated feed. Live samples expire without polling.
      if (!BuildConfig.DEMO && settings.parkOnly) {
        val remaining = RecallPolicy.MAX_AGE_MS - (android.os.SystemClock.elapsedRealtime() - state.sampledAtMs!!)
        handler.postDelayed(expiry, (remaining + 1).coerceAtLeast(1))
      }
    } else hide(true)
    }.onFailure { hide(); WidgetDiagnostics.record(this, "panel telemetry failed ${it.javaClass.simpleName}") }
  }
  private fun show() {
    if (panel != null && !fading) return
    if (fading) removePanel()
    val view = FloatingPanelView.create(this, {
      HomePanelSettings(this).enabled = false; hide()
    }, { id ->
      val preset = PresetStore(this).all().firstOrNull { it.id == id }
      if (preset != null) Toast.makeText(this, Vehicle.recall(this, preset).message, Toast.LENGTH_LONG).show()
    })
    val params = PanelPlacement.params(this)
    windows.addView(view, params); panel = view
    PanelPlacement.draggable(this, view, windows, params)
    PanelMotion.show(view)
    WidgetDiagnostics.record(this, "home panel shown")
  }
  fun hide(animated: Boolean = false) {
    handler.removeCallbacks(expiry)
    if (!animated) homeVisible = false
    val view = panel ?: return
    if (!animated) { removePanel(); return }
    if (fading) return
    fading = true
    // Stop every tap immediately, before the visual fade finishes.
    val params = view.layoutParams as WindowManager.LayoutParams
    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    runCatching { windows.updateViewLayout(view, params) }
    PanelMotion.hide(view) { if (panel === view) removePanel() }
  }
  private fun removePanel() {
    val view = panel
    panel = null; fading = false
    view?.let { it.animate().cancel(); runCatching { windows.removeViewImmediate(it) }; WidgetDiagnostics.record(this, "home panel hidden") }
  }
  override fun onInterrupt() { hide() }
  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); hide() }
  override fun onDestroy() {
    hide(); handler.removeCallbacksAndMessages(null)
    runCatching { if (BuildConfig.DEMO) StorageAccess.prefs(this, "demo-vehicle").unregisterOnSharedPreferenceChangeListener(demoChanges) }
    runCatching { unregisterReceiver(screenOff) }; if (instance === this) reference.clear()
    super.onDestroy()
  }
  companion object {
    private var reference = java.lang.ref.WeakReference<HomeVisibilityService>(null)
    val instance: HomeVisibilityService? get() = reference.get()
  }
}

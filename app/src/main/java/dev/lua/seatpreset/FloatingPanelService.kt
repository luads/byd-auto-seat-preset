package dev.lua.seatpreset

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast

/** Explicit, short-lived placement experiment. No boot path or persisted enabled state. */
class FloatingPanelService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private var panel: View? = null
  private lateinit var windows: WindowManager
  private val expire = Runnable { stopSelf() }
  override fun onBind(intent: Intent?): IBinder? = null
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action != ACTION_SHOW || !Settings.canDrawOverlays(this)) {
      stopSelf(); return START_NOT_STICKY
    }
    if (panel != null) return START_NOT_STICKY
    windows = getSystemService(WINDOW_SERVICE) as WindowManager
    runCatching {
      val view = FloatingPanelView.create(this, { stopSelf() }, {
        Toast.makeText(this, "Display preview only. No seat movement.", Toast.LENGTH_SHORT).show()
      }, previewOnly = true)
      val params = PanelPlacement.params(this)
      windows.addView(view, params)
      panel = view
      PanelPlacement.draggable(this, view, windows, params)
      WidgetDiagnostics.record(this, "floating panel shown duration=30s")
      handler.postDelayed(expire, 30_000)
    }.onFailure {
      WidgetDiagnostics.record(this, "floating panel failed ${it.javaClass.simpleName}")
      Toast.makeText(this, "Could not show the floating panel", Toast.LENGTH_LONG).show()
      stopSelf()
    }
    return START_NOT_STICKY
  }
  override fun onDestroy() {
    handler.removeCallbacks(expire)
    panel?.let { runCatching { windows.removeViewImmediate(it) } }
    panel = null
    WidgetDiagnostics.record(this, "floating panel hidden")
    super.onDestroy()
  }
  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
  companion object { const val ACTION_SHOW = "dev.lua.seatpreset.SHOW_PLACEMENT_TEST" }
}

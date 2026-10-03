package dev.lua.seatpreset

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

@android.annotation.SuppressLint("ApplySharedPref")
object PanelPlacement {
  private fun prefs(c: Context) = StorageAccess.prefs(c, "panel-placement")
  fun width(c: Context) = prefs(c).getInt("width", 380).coerceIn(320, 520)
  fun setWidth(c: Context, width: Int) { check(prefs(c).edit().putInt("width", width.coerceIn(320, 520)).commit()) }
  fun reset(c: Context) { check(prefs(c).edit().clear().commit()) }
  private fun fraction(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
  fun params(c: Context): WindowManager.LayoutParams {
    fun dp(n: Int) = (n * c.resources.displayMetrics.density).toInt()
    val width = minOf(width(c), c.resources.configuration.screenWidthDp - 32).coerceAtLeast(100)
    val height = minOf(width / 2, c.resources.configuration.screenHeightDp - 32).coerceAtLeast(100)
    return WindowManager.LayoutParams(dp(width), dp(height), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      android.graphics.PixelFormat.TRANSLUCENT).apply {
      gravity = android.view.Gravity.TOP or android.view.Gravity.START
      x = (maxX(c, this) * fraction(prefs(c).getFloat("x", 0.97f))).toInt()
      y = (maxY(c, this) * fraction(prefs(c).getFloat("y", 0.24f))).toInt()
    }
  }
  private fun maxX(c: Context, p: WindowManager.LayoutParams) = ((c.resources.configuration.screenWidthDp * c.resources.displayMetrics.density).toInt() - p.width).coerceAtLeast(0)
  private fun maxY(c: Context, p: WindowManager.LayoutParams) = ((c.resources.configuration.screenHeightDp * c.resources.displayMetrics.density).toInt() - p.height).coerceAtLeast(0)
  @android.annotation.SuppressLint("ClickableViewAccessibility")
  fun draggable(c: Context, view: View, windows: WindowManager, p: WindowManager.LayoutParams) {
    var startX = 0; var startY = 0; var downX = 0f; var downY = 0f
    view.findViewWithTag<View>("panel-drag-handle").setOnTouchListener { _, e ->
      when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> { startX = p.x; startY = p.y; downX = e.rawX; downY = e.rawY }
        MotionEvent.ACTION_MOVE -> {
          p.x = (startX + e.rawX - downX).toInt().coerceIn(0, maxX(c, p))
          p.y = (startY + e.rawY - downY).toInt().coerceIn(0, maxY(c, p))
          runCatching { windows.updateViewLayout(view, p) }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> runCatching {
          check(prefs(c).edit().putFloat("x", fraction(p.x.toFloat() / maxX(c, p).coerceAtLeast(1)))
            .putFloat("y", fraction(p.y.toFloat() / maxY(c, p).coerceAtLeast(1))).commit())
        }
      }
      true
    }
  }
}

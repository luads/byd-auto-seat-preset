package dev.lua.seatpreset

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

/** Offline display simulation only. No vehicle reads, writes or preset changes. */
class MotionPreviewActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!StorageAccess.unlocked(this)) { finish(); return }
    if (!BuildConfig.DEMO) { finish(); return }
    fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    val page = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(24), dp(32), dp(24)); setBackgroundColor(getColor(R.color.matte_canvas))
    }
    fun label(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(getColor(R.color.matte_silver)); setPadding(0, dp(12), 0, dp(12)) }
    page.addView(label("Panel motion · simulation", 26f))
    page.addView(label("P shows the panel. Other gears and missing or stale readings hide it. No seat movement.", 16f))
    val status = label("No gear reading", 18f); page.addView(status)
    val stage = FrameLayout(this)
    val panel = FloatingPanelView.create(this, { finish() }) { Toast.makeText(this, "Layout preview only", Toast.LENGTH_SHORT).show() }
    panel.visibility = View.INVISIBLE
    stage.addView(panel, FrameLayout.LayoutParams(dp(minOf(460, resources.configuration.screenWidthDp - 64)), dp(230), Gravity.CENTER))
    val buttons = LinearLayout(this)
    listOf("P", "D", "R", "Unknown", "Stale").forEach { state ->
      buttons.addView(Button(this).apply {
        text = state; isAllCaps = false
        setOnClickListener {
          val now = android.os.SystemClock.elapsedRealtime()
          val snapshot = VehicleSnapshot(if (state == "Unknown") null else if (state == "D") Gear.D else if (state == "R") Gear.R else Gear.P,
            null, null, if (state == "Unknown") null else if (state == "Stale") now - 10_000 else now)
          if (PanelVisibility.allowed(true, true, snapshot, now)) { PanelMotion.show(panel); status.text = "P · panel visible" }
          else { PanelMotion.hide(panel) { panel.visibility = View.INVISIBLE }; status.text = "$state · panel hidden" }
        }
      }, LinearLayout.LayoutParams(0, dp(56), 1f))
    }
    page.addView(buttons); page.addView(stage, LinearLayout.LayoutParams(-1, dp(300)))
    page.addView(Button(this).apply { text = "Back to settings"; isAllCaps = false; setOnClickListener { finish() } })
    setContentView(ScrollView(this).apply { addView(page) })
  }
}

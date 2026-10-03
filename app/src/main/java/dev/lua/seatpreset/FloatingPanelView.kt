package dev.lua.seatpreset

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shared native preview and overlay view. Callbacks are wired only by the caller. */
object FloatingPanelView {
  fun create(context: Context, close: () -> Unit, recall: (String) -> Unit): View {
    fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun shape(color: Int, radius: Int) = GradientDrawable().apply {
      setColor(color); cornerRadius = dp(radius).toFloat()
      setStroke(dp(1), 0xFF303841.toInt())
    }
    fun label(value: String, size: Float, color: Int) = TextView(context).apply {
      text = value; textSize = size; setTextColor(context.getColor(color))
      typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
      maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val root = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(12))
      background = shape(context.getColor(R.color.matte_surface), 20)
    }
    val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    header.addView(label("Seat presets  ⋮⋮", 14f, R.color.matte_muted).apply { tag = "panel-drag-handle"; contentDescription = "Drag panel"; gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(48), 1f))
    header.addView(label("×", 26f, R.color.matte_silver).apply {
      gravity = Gravity.CENTER; contentDescription = "Close floating panel"
      isClickable = true; setOnClickListener { close() }
    }, LinearLayout.LayoutParams(dp(48), dp(48)))
    root.addView(header)
    val targets = LinearLayout(context)
    PresetStore(context).favourites().forEachIndexed { slot, preset ->
      val target = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(8), dp(6), dp(8), dp(8))
        val tile = shape(context.getColor(R.color.matte_raised), 18)
        background = RippleDrawable(ColorStateList.valueOf(0x445AA8ED), tile, null)
        elevation = dp(1).toFloat()
        isClickable = true; isFocusable = true
        contentDescription = "Recall preset for ${preset.name}"
        setOnClickListener { recall(preset.id) }
      }
      target.addView(ImageView(context).apply {
        setImageResource(R.drawable.seat_matte); scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
      }, LinearLayout.LayoutParams(-1, 0, 1f))
      target.addView(label(preset.name, 23f, R.color.matte_silver).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
      if (preset.position == null) {
        target.addView(label("Not saved", 12f, R.color.matte_muted).apply { gravity = Gravity.CENTER })
        target.isEnabled = false; target.alpha = 0.65f
      }
      targets.addView(target, LinearLayout.LayoutParams(0, -1, 1f).apply { if (slot == 0) marginEnd = dp(5) else marginStart = dp(5) })
    }
    root.addView(targets, LinearLayout.LayoutParams(-1, 0, 1f))
    return root
  }
}

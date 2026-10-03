package dev.lua.seatpreset

import android.app.Activity
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Debug-only scale study. Does not access vehicle or preset state. */
class IconPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(20), dp(32), dp(16))
            setBackgroundColor(Color.rgb(15, 18, 23))
        }
        root.addView(label("Seat Presets · icon scale study", 26f))
        root.addView(label("64 / 80 / 96 dp at emulator density 160. BYD launcher dimensions still unmeasured.", 16f))
        val samples = listOf(
            "01-sculpted-seat" to "1 · Original sculpted",
            "02-seat-star-symbol" to "2 · Original symbol",
            "03-two-drivers" to "3 · Two drivers",
            "04-favourite-emblem" to "4 · Favourite emblem",
            "01a-matte-seat" to "1A · Matte / wider framing",
            "01b-close-seat" to "1B · Satin / closer framing",
            "02a-flat-symbol" to "2A · Flat / wider framing",
            "02b-soft-relief" to "2B · Soft relief / closer framing"
        )
        for (pair in samples.chunked(2)) {
            val row = LinearLayout(this)
            for ((file, title) in pair) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(12), dp(20), dp(8))
                }
                card.addView(label(title, 18f))
                val icons = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                val bitmap = assets.open("icon-concepts/$file.png").use { BitmapFactory.decodeStream(it) }
                for (size in listOf(64, 80, 96)) {
                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                    }
                    cell.addView(ImageView(this).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }, LinearLayout.LayoutParams(dp(size), dp(size)))
                    cell.addView(label("${size} dp", 13f))
                    icons.addView(cell, LinearLayout.LayoutParams(dp(140), dp(126)))
                }
                card.addView(icons)
                row.addView(card, LinearLayout.LayoutParams(0, dp(194), 1f))
            }
            root.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(226, 232, 239))
        setPadding(0, 0, 0, dp(8))
    }
}

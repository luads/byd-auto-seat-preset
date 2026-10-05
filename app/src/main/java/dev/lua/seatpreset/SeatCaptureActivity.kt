package dev.lua.seatpreset

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import java.util.concurrent.Executors

/** Foreground capture/contract trial. Stores observations, never sends a movement command. */
class SeatCaptureActivity : Activity() {
  private val worker = Executors.newSingleThreadExecutor()
  private var generation = 0
  private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
  private var reading: Position? = null
  private var readAt = 0L
  private lateinit var output: TextView
  private lateinit var bridge: OemReadBridge
  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    if (!StorageAccess.unlocked(this)) { finish(); return }
    bridge = OemReadBridge(this)
    val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24); setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun label(s: String, size: Float = 17f) = TextView(this).apply { text = s; textSize = size; setTextColor(getColor(R.color.matte_silver)); setPadding(0, 12, 0, 12) }
    fun button(s: String, click: () -> Unit) = Button(this).apply { text = s; isAllCaps = false; setOnClickListener { runCatching(click).onFailure { output.text = it.message ?: "Trial unavailable" } } }
    page.addView(label("Seat position trial", 28f))
    page.addView(label("Read and save the driver's current coordinates. These are experimental OEM observations, not a validated movement preset. Park the car and use native controls to compare A → B → A. No seat movement is sent here."))
    output = label("Tap Read current position. Getter return times are not proof that the OEM cache is fresh.", 21f); page.addView(output)
    page.addView(button("Read current position") { read() })
    page.addView(button("Save readings to this preset") {
      val preset = PresetStore(this).all().firstOrNull { it.id == intent.getStringExtra("preset-id") } ?: error("Open this trial from a preset's Edit menu")
      check(reading != null && SystemClock.elapsedRealtime() - readAt <= 30_000) { "Read the current position first" }
      AlertDialog.Builder(this).setTitle("Save experimental readings for ${preset.name}?")
        .setMessage("Confirm that this is the driver's seat and the car is parked. This replaces saved coordinates. Automatic and normal recall will reject these unverified observations.")
        .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
          val observed = reading ?: return@setPositiveButton
          runCatching {
            check(SystemClock.elapsedRealtime() - readAt <= 30_000) { "Read again before saving" }
            val store = PresetStore(this)
            store.save(store.all().map { if (it.id == preset.id) it.copy(position = observed, capturedAtMs = System.currentTimeMillis()) else it })
            output.text = "Experimental seat readings saved. Normal recall remains disabled."
          }.onFailure { output.text = it.message ?: "Save failed" }
        }.show()
    })
    page.addView(button("Inspect loading this preset") {
      val target = PresetStore(this).all().firstOrNull { it.id == intent.getStringExtra("preset-id") }?.position ?: error("Save seat readings first")
      val current = reading ?: error("Read current position first")
      check(SystemClock.elapsedRealtime() - readAt <= 30_000) { "Read again before inspecting" }
      output.text = SeatObservation.plan(current, target) + "\n\nNo command sent. Before a supervised write, verify axis units/ranges, fresh P/brake readings and interruption on this firmware. Setter return is not movement completion."
    })
    page.addView(button("Back") { finish() })
    setContentView(ScrollView(this).apply { addView(page) })
  }
  private fun read() {
    if (!inFlight.compareAndSet(false, true)) return
    val token = ++generation; reading = null; output.text = "Reading…"
    worker.execute {
      val outcome = runCatching {
        val a = bridge.seat(); Thread.sleep(200); val b = bridge.seat()
        check(SeatObservation.repeatable(a, b)) { "Position changed during capture. Read again." }
        b to bridge.rawPark()
      }
      inFlight.set(false)
      runOnUiThread { if (!isFinishing && token == generation) outcome.fold({ (p, park) ->
        reading = p; readAt = SystemClock.elapsedRealtime()
        output.text = p.coordinates.entries.joinToString("\n") { "${it.key}: ${it.value}" } + "\ngearMode: ${park.first ?: "unavailable"} · brake: ${park.second ?: "unavailable"}\nObserved at uptime ${readAt}ms. Driver area 1. Units/cache freshness remain unverified."
        PrivateDiagnostics.record(this, "seat observation at=$readAt values=${p.coordinates} rawPark=$park")
      }, { e -> output.text = "Read failed: ${e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName}. No preset was changed."; PrivateDiagnostics.record(this, "seat read failed type=${e.javaClass.simpleName}") }) }
    }
  }
  override fun onStop() { generation++; reading = null; super.onStop() }
  override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}

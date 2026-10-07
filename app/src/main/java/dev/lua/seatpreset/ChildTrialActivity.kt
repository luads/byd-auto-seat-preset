package dev.lua.seatpreset

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.*
import java.util.concurrent.Executors

/** Ten-second foreground trial only. Recognition produces a screen message, never recall. */
class ChildTrialActivity : Activity() {
  private val worker = Executors.newSingleThreadExecutor()
  private val main = Handler(Looper.getMainLooper())
  private var generation = 0
  private var busy = false
  private var trialSummary = "no samples"
  private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
  private lateinit var output: TextView
  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    if (!StorageAccess.unlocked(this)) { finish(); return }
    val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24); setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun label(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(getColor(R.color.matte_silver)); setPadding(0, 12, 0, 12) }
    page.addView(label("Child-lock input trial", 28f))
    page.addView(label("Park the car. Arm for ten seconds, wait for READY, then double-press one child-lock control about half a second apart. Recognition changes this screen only. Startup values are not presses.", 18f))
    output = label("Not armed", 24f); page.addView(output)
    page.addView(Button(this).apply { text = "Arm · 10 seconds"; isAllCaps = false; setOnClickListener { arm() } })
    page.addView(Button(this).apply { text = "Stop"; isAllCaps = false; setOnClickListener { stop() } })
    page.addView(Button(this).apply { text = "Back"; isAllCaps = false; setOnClickListener { finish() } })
    setContentView(ScrollView(this).apply { addView(page) })
  }
  private fun arm() {
    if (busy || inFlight.get()) return
    busy = true; trialSummary = "no samples"; val token = ++generation
    output.text = "Connecting. Wait for READY…"
    val started = SystemClock.elapsedRealtime()
    val recognizer = ChildLockRecognizer()
    val bridge = OemReadBridge(this)
    var count = 0; var valid = 0; var events = 0; var failures = 0
    var last = started; var maxGap = 0L
    PrivateDiagnostics.record(this, "child trial START at=$started")
    fun finishTrial() {
      busy = false
      output.text = "Finished: $events gestures, $valid valid samples, $failures unavailable, max gap ${maxGap}ms. No seat action."
      PrivateDiagnostics.record(this, "child trial FINISH samples=$count valid=$valid events=$events unavailable=$failures maxGap=$maxGap")
    }
    lateinit var tick: Runnable
    tick = Runnable {
      if (token != generation) return@Runnable
      if (SystemClock.elapsedRealtime() - started >= 10_000) { finishTrial(); return@Runnable }
      if (!inFlight.compareAndSet(false, true)) return@Runnable
      worker.execute {
        val pair = runCatching { bridge.children() }.getOrElse { null to null }
        val parked = runCatching { bridge.rawPark().first == 1 }.getOrDefault(false)
        val now = SystemClock.elapsedRealtime()
        inFlight.set(false)
        main.post {
          if (token != generation) return@post
          if (now - started >= 10_000) { finishTrial(); return@post }
          if (count > 0) maxGap = maxOf(maxGap, now - last)
          last = now; count++
          val sample = ChildSample(now, pair.first, pair.second)
          if (pair.first != null && pair.second != null) valid++ else failures++
          trialSummary = "samples=$count valid=$valid unavailable=$failures last=$pair maxGap=$maxGap"
          val side = if (parked) recognizer.sample(sample) else { recognizer.reset(); null }
          PrivateDiagnostics.record(this, "child sample at=$now left=${pair.first} right=${pair.second} gesture=$side")
          if (side != null) {
            events++
            val mapped = runCatching { ChildBindings(this).preset(side) }.getOrNull()
            output.text = "${mapped?.name ?: "No preset assigned"} · $side double · event $events\nP reported. Screen feedback only; no seat command."
          }
          else if (events == 0) output.text = if (!parked) "Select P to test bindings" else if (pair.first != null && pair.second != null) "READY · left ${pair.first}, right ${pair.second}" else "Not ready: both child-lock states must be readable"
          main.postDelayed(tick, 100)
        }
      }
    }
    main.postDelayed({ if (token == generation && busy) { generation++; finishTrial() } }, 10_000)
    main.post(tick)
  }
  private fun stop() { if (busy) PrivateDiagnostics.record(this, "child trial CANCEL $trialSummary"); generation++; busy = false; main.removeCallbacksAndMessages(null); if (::output.isInitialized) output.text = "Stopped. No seat action." }
  override fun onStop() { stop(); super.onStop() }
  override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}

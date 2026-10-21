package dev.lua.seatpreset

import android.content.Context
import android.os.SystemClock

class UnsupportedVehicle : VehicleAdapter {
  override val source = "unverified"
  override val coordinateFormat = "unverified"
  override val recallValidated = false
  override fun snapshot() = VehicleSnapshot(null, null, null, null)
  override fun capture(): Position? = null
  override fun apply(position: Position): Boolean = false
}

@android.annotation.SuppressLint("ApplySharedPref")
class DemoVehicle(context: Context) : VehicleAdapter {
  private val prefs = StorageAccess.prefs(context, "demo-vehicle")
  override val source = "simulation"
  override val coordinateFormat = "simulation-v1"
  override val recallValidated = true
  override fun snapshot(): VehicleSnapshot {
    val now = SystemClock.elapsedRealtime()
    Startup.times.sampled(now)
    val freshness = prefs.getString("freshness", "Fresh")
    return VehicleSnapshot(
      if (freshness == "Unavailable") null else Gear.valueOf(prefs.getString("gear", "P")!!),
      if (freshness == "Unavailable") null else prefs.getBoolean("brake", true),
      null,
      when (freshness) { "Unavailable" -> null; "Stale" -> now - 10_000; else -> now },
    )
  }
  override fun accepts(position: Position) = DemoCoordinates.accepts(position)
  override fun capture(): Position {
    val value = prefs.getInt("position", 40).toDouble()
    Startup.times.sampled(SystemClock.elapsedRealtime())
    return Position(mapOf("demo-axis" to value), source, coordinateFormat)
  }
  override fun apply(position: Position): Boolean {
    if (!accepts(position)) return false
    return prefs.edit().putInt("position", position.coordinates.getValue("demo-axis").toInt()).commit() && capture() == position
  }
  fun configure(gear: String, brake: Boolean, freshness: String, position: Int) {
    prefs.edit().putString("gear", gear).putBoolean("brake", brake).putString("freshness", freshness).putInt("position", position).commit()
  }
  fun gear() = prefs.getString("gear", "P")!!
  fun brake() = prefs.getBoolean("brake", true)
  fun freshness() = prefs.getString("freshness", "Fresh")!!
  fun position() = prefs.getInt("position", 40)
}

object Vehicle {
  private val recallWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
  private val inFlight=java.util.concurrent.atomic.AtomicBoolean(false)
  fun requestRecall(context: Context,preset: Preset,axes: Set<String>?=null,done: (RecallResult)->Unit) {
    if(!inFlight.compareAndSet(false,true)) { done(RecallResult(false,"A seat request is already running"));return }
    val app=context.applicationContext
    val requestedAt=SystemClock.elapsedRealtime()
    recallWorker.execute {
      val result=try { recall(app,preset,axes,requestedAt) } finally { inFlight.set(false) }
      android.os.Handler(android.os.Looper.getMainLooper()).post { done(result) }
    }
  }
  fun adapter(context: Context): VehicleAdapter = if (BuildConfig.DEMO) DemoVehicle(context) else LiveVehicle(context)
  fun recall(context: Context, preset: Preset,axes: Set<String>?=null,requestedAt: Long=SystemClock.elapsedRealtime()): RecallResult {
    if (!BuildConfig.DEMO && !SeatControl.enabled(context)) return RecallResult(false,"Enable Seat movement in Settings first")
    if (!StorageAccess.unlocked(context)) return RecallResult(false, "Storage is not ready")
    return runCatching {
      val store = PresetStore(context)
      val result = if (store.storageMessage() != null) RecallResult(false, "Review preset storage in Settings before use")
        else RecallController(if(BuildConfig.DEMO) adapter(context) else LiveVehicle(context,axes,requestedAt), SystemClock::elapsedRealtime).recall(preset)
      runCatching { store.result = result.message }
      runCatching { PresetWidget.refresh(context) }
      result
    }.getOrElse { RecallResult(false, "Recall unavailable") }
  }
}

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
  fun adapter(context: Context): VehicleAdapter = if (BuildConfig.DEMO) DemoVehicle(context) else UnsupportedVehicle()
  fun recall(context: Context, preset: Preset): RecallResult {
    if (!StorageAccess.unlocked(context)) return RecallResult(false, "Storage is not ready")
    return runCatching {
      val store = PresetStore(context)
      val result = if (store.storageMessage() != null) RecallResult(false, "Review preset storage in Settings before use")
        else RecallController(adapter(context), SystemClock::elapsedRealtime).recall(preset)
      runCatching { store.result = result.message }
      runCatching { PresetWidget.refresh(context) }
      result
    }.getOrElse { RecallResult(false, "Recall unavailable") }
  }
}

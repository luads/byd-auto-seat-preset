package dev.lua.seatpreset

enum class Gear { P, R, N, D }
data class VehicleSnapshot(val gear: Gear?, val parkingBrake: Boolean?, val speedKph: Double?, val sampledAtMs: Long?)
data class Position(val coordinates: Map<String, Double>, val source: String, val coordinateFormat: String)
data class Preset(val id: String, val name: String, val position: Position? = null, val capturedAtMs: Long? = null)
data class RecallResult(val allowed: Boolean, val message: String)

object RecallPolicy {
  const val MAX_AGE_MS = 2_000L
  fun blockReason(snapshot: VehicleSnapshot, nowMs: Long): String? {
    val sampled = snapshot.sampledAtMs ?: return "Vehicle readings unavailable"
    if (sampled < 0 || nowMs < 0 || sampled > nowMs || nowMs - sampled > MAX_AGE_MS) return "Vehicle readings are stale"
    if (snapshot.gear != Gear.P) return "Park in P before recalling a preset"
    if (snapshot.parkingBrake != true) return "Parking brake must be confirmed"
    val speed = snapshot.speedKph
    if (speed != null && (!speed.isFinite() || speed != 0.0)) return "Movement detected or speed invalid"
    return null
  }
}

interface VehicleAdapter {
  val source: String
  val coordinateFormat: String
  val recallValidated: Boolean
  fun accepts(position: Position): Boolean = position.source == source && position.coordinateFormat == coordinateFormat
  fun snapshot(): VehicleSnapshot
  fun capture(): Position?
  fun apply(position: Position): Boolean
}

class RecallController(private val vehicle: VehicleAdapter, private val clock: () -> Long) {
  fun recall(preset: Preset): RecallResult {
    if (!vehicle.recallValidated) return RecallResult(false, "Seat control is not available yet")
    val position = preset.position ?: return RecallResult(false, "Save a position for ${preset.name} first")
    if (position.source != vehicle.source || position.coordinateFormat != vehicle.coordinateFormat ||
      position.coordinates.isEmpty() || position.coordinates.values.any { !it.isFinite() } || !vehicle.accepts(position)) {
      return RecallResult(false, "Preset is incompatible with this vehicle")
    }
    RecallPolicy.blockReason(vehicle.snapshot(), clock())?.let { return RecallResult(false, it) }
    return try {
      if (!vehicle.apply(position)) RecallResult(false, "Recall could not be verified")
      else RecallResult(true, "${preset.name}: preset recalled")
    } catch (_: Exception) { RecallResult(false, "Recall failed") }
  }
}

object FavouriteSlots {
  fun assign(current: List<String>, slot: Int, id: String): List<String> {
    require(current.size == 2 && current.distinct().size == 2 && slot in 0..1)
    return current.toMutableList().apply {
      val other = 1 - slot
      if (this[other] == id) this[other] = this[slot]
      this[slot] = id
    }
  }
}

object DemoCoordinates {
  fun accepts(position: Position): Boolean {
    val value = position.coordinates["demo-axis"] ?: return false
    return position.source == "simulation" && position.coordinateFormat == "simulation-v1" && position.coordinates.keys == setOf("demo-axis") && value.isFinite() && value in 0.0..100.0 && value == value.toInt().toDouble()
  }
}

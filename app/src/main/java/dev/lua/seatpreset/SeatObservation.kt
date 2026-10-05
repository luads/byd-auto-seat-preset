package dev.lua.seatpreset

/** Candidate OEM observations are saved separately by format and never accepted by normal recall. */
internal object SeatObservation {
  const val FORMAT = "byd-setting-driver-1-unverified-v1"
  val axes = setOf("horizontal", "backrest", "height")
  fun usable(values: Map<String, Double>) = values.keys == axes && values.values.all { it.isFinite() && it in 0.0..100.0 } && values.values.any { it != 0.0 }
  fun repeatable(a: Position, b: Position) = a.source == b.source && a.coordinateFormat == FORMAT && b.coordinateFormat == FORMAT && usable(a.coordinates) && a.coordinates == b.coordinates
  fun plan(current: Position, target: Position): String {
    require(repeatable(target, target) && current.source == target.source && current.coordinateFormat == FORMAT && usable(current.coordinates)) { "Capture compatible seat readings first" }
    val methods = mapOf("horizontal" to "turnSeatHorizationPercent", "backrest" to "turnSeatbackrestPercent", "height" to "turnSeatHeightPercent")
    return axes.joinToString("\n") { axis -> "$axis: ${current.coordinates.getValue(axis)} → ${target.coordinates.getValue(axis)} (${methods.getValue(axis)}, driver area 1)" }
  }
}

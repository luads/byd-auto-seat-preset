package dev.lua.seatpreset

/** Candidate OEM observations are saved separately by format and never accepted by normal recall. */
internal object SeatObservation {
  const val FORMAT = PositionFeedback.FORMAT
  val axes = setOf("horizontal", "backrest", "height")
  fun usable(values: Map<String, Double>) = values.keys == axes && values.values.all { it.isFinite() && it in 0.0..100.0 } && values.values.any { it != 0.0 }
  fun repeatable(a: Position, b: Position) = a.source == b.source && a.coordinateFormat == FORMAT && b.coordinateFormat == FORMAT && (PositionFeedback.accepts(a) && PositionFeedback.accepts(b) || usable(a.coordinates) && usable(b.coordinates)) && a.coordinates == b.coordinates
  fun plan(current: Position, target: Position): String {
    require(repeatable(target, target) && current.source == target.source && current.coordinateFormat == FORMAT && (PositionFeedback.accepts(current) || usable(current.coordinates))) { "Capture compatible seat readings first" }
    val methods = mapOf("horizontal" to "native horizontal signals", "backrest" to "native backrest signals", "height" to "native height signals")
    return axes.joinToString("\n") { axis -> "$axis: ${current.coordinates.getValue(axis)} → ${target.coordinates.getValue(axis)} (${methods.getValue(axis)}, driver area 1)" }
  }
}

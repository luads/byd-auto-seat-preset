package dev.lua.seatpreset

/** In-memory, single-axis supervised test contract. Never authorizes normal/automatic recall. */
internal class SeatMotionTrial {
  private val samples = mutableListOf<Position>()
  private var pending: Pending? = null
  private var lastRecordedAt = -1L
  data class Pending(val axis: String, val target: Int, val source: String, val at: Long)
  @Synchronized fun record(position: Position, atMs: Long) {
    pending = null
    try {
      require(atMs >= 0 && atMs >= lastRecordedAt)
      require(SeatObservation.usable(position.coordinates) && position.coordinateFormat == SeatObservation.FORMAT)
      if (samples.size == 3) samples.clear()
      samples += position; lastRecordedAt = atMs
    } catch (e: Exception) { reset(); throw e }
  }
  @Synchronized fun validated(axis: String): Boolean {
    if (axis !in SeatObservation.axes || samples.size != 3) return false
    val (a,b,c) = samples
    return SeatObservation.repeatable(a,c) && a.source == b.source && b.coordinateFormat == a.coordinateFormat &&
      SeatObservation.axes.all { if (it == axis) a.coordinates[it] != b.coordinates[it] else a.coordinates[it] == b.coordinates[it] } &&
      listOf(a,b,c).all { p -> p.coordinates.getValue(axis).let { it > 0 && it < 100 && it == it.toInt().toDouble() } }
  }
  @Synchronized fun prepare(axis: String, current: Position, target: Position, now: Long): Pending {
    pending = null
    require(now >= lastRecordedAt && now - lastRecordedAt <= 300_000 && validated(axis)) { "Verify this axis with native A → B → A first" }
    require(now >= 0 && current.source == samples.first().source && SeatObservation.repeatable(target,target) && current.source == target.source && current.coordinateFormat == target.coordinateFormat && SeatObservation.usable(current.coordinates))
    val from = current.coordinates.getValue(axis); val to = target.coordinates.getValue(axis)
    require(from > 0 && from < 100 && to > 0 && to < 100 && from == from.toInt().toDouble() && to == to.toInt().toDouble() && kotlin.math.abs(to-from) in 1.0..2.0) { "First trial must move one axis by only 1–2 candidate units" }
    return Pending(axis,to.toInt(),current.source,now).also { pending=it }
  }
  @Synchronized fun consume(now: Long, parkedEvidence: VehicleSnapshot, foreground: Boolean): Pending {
    val command = pending ?: error("Prepare a new test")
    pending = null
    require(foreground && now >= command.at && now-command.at <= RecallPolicy.MAX_AGE_MS) { "Test confirmation expired" }
    RecallPolicy.blockReason(parkedEvidence,now)?.let { error(it) }
    return command
  }
  @Synchronized fun reset() { pending=null; samples.clear();lastRecordedAt=-1 }
}

package dev.lua.seatpreset

enum class ChildSide { LEFT, RIGHT }
data class ChildSample(val atMs: Long, val left: Int?, val right: Int?)

/** Exclusive state-return gesture. In-memory only; never dispatches a vehicle action. */
class ChildLockRecognizer(private val pairMs: Long = 1_000, private val gapMs: Long = 250) {
  private data class Pending(val side: ChildSide, val initial: Int, val atMs: Long)
  private var previous: ChildSample? = null
  private var pending: Pending? = null
  init { require(pairMs > 0 && gapMs > 0) }
  fun reset() { previous = null; pending = null }
  fun sample(next: ChildSample): ChildSide? {
    if (next.atMs < 0 || next.left !in 1..2 || next.right !in 1..2) { reset(); return null }
    val before = previous
    previous = next
    if (before == null) return null
    if (next.atMs <= before.atMs || next.atMs - before.atMs > gapMs) { pending = null; return null }
    val left = next.left != before.left; val right = next.right != before.right
    val pair = pending
    if (pair != null && next.atMs - pair.atMs > pairMs) { pending = null; return null }
    if (!left && !right) return null
    if (left && right) { pending = null; return null }
    val side = if (left) ChildSide.LEFT else ChildSide.RIGHT
    if (pair == null) {
      pending = Pending(side, (if (left) before.left else before.right)!!, next.atMs)
      return null
    }
    pending = null
    if (pair.side != side) return null
    return side.takeIf { (if (left) next.left else next.right) == pair.initial }
  }
}

object ChildReplay {
  // Edge times are observed intervals from the lab handoff, not physical press timestamps.
  fun samples(side: ChildSide, gaps: List<Long>): List<ChildSample> {
    var time = 0L; var value = if (side == ChildSide.LEFT) 2 else 1
    val result = mutableListOf(ChildSample(0, 2, 1))
    fun point(at: Long) = ChildSample(at, if (side == ChildSide.LEFT) value else 2, if (side == ChildSide.RIGHT) value else 1)
    fun holdUntil(end: Long) { while (time + 100 < end) { time += 100; result += point(time) }; time = end }
    holdUntil(100); value = if (value == 1) 2 else 1; result += point(time)
    gaps.forEachIndexed { index, gap ->
      holdUntil(time + gap); value = if (value == 1) 2 else 1; result += point(time)
      if (index < gaps.lastIndex) { holdUntil(time + 100); value = if (value == 1) 2 else 1; result += point(time) }
    }
    return result
  }
}

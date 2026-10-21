package dev.lua.seatpreset

/** Physical recall produced unintended travel. Reopen only after the coordinate contract is verified. */
internal object SeatWritePolicy {
  const val BLOCK_REASON = "Seat movement paused while saved coordinates are investigated"
  fun allowed(): Boolean = false
}

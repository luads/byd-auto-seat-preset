package dev.lua.seatpreset

/** Only the new typed-feedback native-signal loader is enabled. Absolute writers remain retired. */
internal object SeatWritePolicy {
  const val BLOCK_REASON="Record a fresh position before using native feedback loading"
  fun allowed():Boolean=true
  fun absoluteAllowed():Boolean=false
}

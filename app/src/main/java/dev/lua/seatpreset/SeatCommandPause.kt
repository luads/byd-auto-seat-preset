package dev.lua.seatpreset

/** Minimum release-to-next-command interval. Feedback time counts toward this interval. */
internal object SeatCommandPause {
  val choices=listOf(0L,100L,200L,300L,500L,750L,1_000L)
  const val DEFAULT_MS=500L
  fun valid(value:Long)=value in choices
}

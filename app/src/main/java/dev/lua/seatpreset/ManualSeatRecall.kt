package dev.lua.seatpreset

/** One foreground owner-confirmed recall. No calibration or persisted movement request. */
internal class ManualSeatRecall {
  data class Axis(val name: String, val from: Int, val to: Int)
  private var consentAt: Long? = null
  @Synchronized fun confirm(at: Long) { require(at >= 0);consentAt=at }
  @Synchronized fun cancel() { consentAt=null }
  @Synchronized fun consume(now: Long, foreground: Boolean) {
    val at=consentAt;consentAt=null
    require(at != null && foreground && now >= at && now-at <= RecallPolicy.MAX_AGE_MS) { "Confirmation expired. Tap Move again." }
  }
  companion object {
    fun plan(current: Position,target: Position): List<Axis> {
      require(SeatObservation.repeatable(current,current) && SeatObservation.repeatable(target,target) && current.source==target.source) { "Save a compatible position first" }
      return SeatObservation.axes.mapNotNull { axis ->
        val from=current.coordinates.getValue(axis);val to=target.coordinates.getValue(axis)
        // Zero can be the adapter fallback. Do not drive an unobserved axis to an endpoint.
        if(from==0.0 || to==0.0) return@mapNotNull null
        require(from==from.toInt().toDouble() && to==to.toInt().toDouble() && from in 1.0..99.0 && to in 1.0..99.0) { "Unsupported $axis coordinates" }
        Axis(axis,from.toInt(),to.toInt())
      }
    }
  }
}

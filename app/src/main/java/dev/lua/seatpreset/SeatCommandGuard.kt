package dev.lua.seatpreset

/** Pure dispatch guard. Recheck after preparation/logging and before every individual axis. */
internal object SeatCommandGuard {
  fun dispatch(axes: List<ManualSeatRecall.Axis>, requestedAt: Long, clock: ()->Long,
      enabled: ()->Boolean, snapshot: ()->VehicleSnapshot,
      prepare: (ManualSeatRecall.Axis)->Unit, write: (ManualSeatRecall.Axis)->Unit) {
    fun checkNow() {
      check(enabled()) { "Seat movement is off" }
      val state=snapshot();val now=clock()
      check(requestedAt>=0 && now>=requestedAt && now-requestedAt<=RecallPolicy.MAX_AGE_MS) { "Recall request expired" }
      RecallPolicy.blockReason(state,now)?.let { error(it) }
    }
    axes.forEach { axis -> checkNow();prepare(axis);checkNow();write(axis) }
  }
}

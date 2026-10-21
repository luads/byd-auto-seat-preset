package dev.lua.seatpreset

/** Cancelling acceptance never releases an outstanding OEM call or authorizes another one. */
internal class SeatTrialReadGuard(private val timeoutMs: Long = 20_000) {
  data class Ticket(val id: Long, val beganMs: Long, val deadlineMs: Long)
  private var sequence = 0L
  private var running: Ticket? = null
  private var accepted: Long? = null
  init { require(timeoutMs > 0) }
  @Synchronized fun begin(now: Long): Ticket? {
    require(now >= 0 && now <= Long.MAX_VALUE - timeoutMs)
    if (running != null) return null
    return Ticket(++sequence, now, now + timeoutMs).also { running = it; accepted = it.id }
  }
  @Synchronized fun busy() = running != null
  @Synchronized fun cancel(ticket: Ticket): Boolean {
    if (running != ticket || accepted != ticket.id) return false
    accepted = null
    return true
  }
  @Synchronized fun expire(ticket: Ticket, now: Long): Boolean =
    now >= ticket.deadlineMs && cancel(ticket)
  @Synchronized fun finish(ticket: Ticket, now: Long): Boolean {
    if (running != ticket) return false
    val valid = accepted == ticket.id && now >= ticket.beganMs && now < ticket.deadlineMs
    running = null; accepted = null
    return valid
  }
}

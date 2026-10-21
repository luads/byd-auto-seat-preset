package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class SeatTrialReadGuardTest {
  @Test fun deadlineInvalidatesResultWithoutAllowingAnotherOutstandingRead() {
    val guard = SeatTrialReadGuard(20)
    val ticket = guard.begin(100)!!
    assertFalse(guard.expire(ticket, 119))
    assertTrue(guard.expire(ticket, 120))
    assertTrue(guard.busy()); assertNull(guard.begin(121))
    assertFalse(guard.finish(ticket, 130))
    assertFalse(guard.busy()); assertNotNull(guard.begin(131))
  }
  @Test fun cancellationNeverReleasesOrAcceptsTheStillRunningOemCall() {
    val guard = SeatTrialReadGuard()
    val ticket = guard.begin(1)!!
    assertTrue(guard.cancel(ticket)); assertFalse(guard.cancel(ticket))
    assertNull(guard.begin(2))
    assertFalse(guard.finish(ticket, 3))
    assertNotNull(guard.begin(4))
  }
  @Test fun lateCompletionIsRejectedEvenIfDeadlineCallbackHasNotRun() {
    val guard = SeatTrialReadGuard(20)
    assertFalse(guard.finish(guard.begin(100)!!, 120))
    assertFalse(guard.busy())
  }
  @Test fun staleTicketCannotCancelOrReleaseLaterRead() {
    val guard = SeatTrialReadGuard(20)
    val first = guard.begin(100)!!
    assertTrue(guard.finish(first, 101))
    val second = guard.begin(102)!!
    assertFalse(guard.cancel(first)); assertFalse(guard.finish(first, 103))
    assertFalse(guard.expire(first, 999)); assertNull(guard.begin(103))
    assertTrue(guard.finish(second, 104))
  }
}

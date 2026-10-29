package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class SeatWritePolicyTest {
  @Test fun nativeFeedbackLoadingIsEnabledButRetiredAbsoluteWritersStayBlocked() { assertTrue(SeatWritePolicy.allowed());assertFalse(SeatWritePolicy.absoluteAllowed()) }
}

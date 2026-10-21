package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class SeatWritePolicyTest {
  @Test fun incidentBuildCannotDispatchLiveSeatWrites() { assertFalse(SeatWritePolicy.allowed()) }
}

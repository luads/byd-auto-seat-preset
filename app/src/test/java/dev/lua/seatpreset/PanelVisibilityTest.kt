package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class PanelVisibilityTest {
  private val fresh = VehicleSnapshot(Gear.P, null, null, 1000)
  @Test fun freshParkOnHomeOnly() {
    assertTrue(PanelVisibility.allowed(true, true, fresh, 1500))
    assertFalse(PanelVisibility.allowed(false, true, fresh, 1500))
    assertFalse(PanelVisibility.allowed(true, false, fresh, 1500))
    listOf(Gear.D, Gear.N, Gear.R, null).forEach { assertFalse(PanelVisibility.allowed(true, true, fresh.copy(gear = it), 1500)) }
  }
  @Test fun missingStaleAndFutureReadingsHide() {
    listOf(null, -1001L, 1501L).forEach { assertFalse(PanelVisibility.allowed(true, true, fresh.copy(sampledAtMs = it), 1500)) }
  }
  @Test fun invalidTimestampCannotWrapIntoFreshPark() {
    assertFalse(PanelVisibility.allowed(true, true, fresh.copy(sampledAtMs = Long.MIN_VALUE), 1500))
    assertFalse(PanelVisibility.allowed(true, true, fresh, -1))
    assertEquals("Vehicle readings are stale", RecallPolicy.blockReason(fresh.copy(parkingBrake = true, sampledAtMs = Long.MIN_VALUE), 1500))
  }
  @Test fun layoutOverrideNeverOverridesHomeOrEnabled() {
    assertTrue(PanelVisibility.allowed(true, true, fresh.copy(gear = null), 1500, false))
    assertFalse(PanelVisibility.allowed(false, true, fresh, 1500, false))
    assertFalse(PanelVisibility.allowed(true, false, fresh, 1500, false))
  }
}

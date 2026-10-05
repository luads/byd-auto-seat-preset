package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class GearModeReadTest {
  @Test fun measuredCodesArePreserved() {
    for (code in 1..4) assertEquals(code, GearModeRead.fromJson("{\"gearMode\":$code}"))
  }
  @Test fun missingMalformedAndUnknownReadingsStayUnknown() {
    for (row in listOf(null, "", "broken", "{}", "{\"gearMode\":null}", "{\"gearMode\":0}", "{\"gearMode\":5}"))
      assertNull(GearModeRead.fromJson(row))
  }
  @Test fun noTypeCoercionOrFractionalState() {
    for (value in listOf("true", "\"1\"", "1.5", "1.0", "[]", "{}", "4294967297"))
      assertNull(GearModeRead.fromJson("{\"gearMode\":$value}"))
  }
  @Test fun unrelatedIdentityFieldsAreNotReturned() {
    assertEquals(1, GearModeRead.fromJson("{\"virtualVin\":\"synthetic-test-only\",\"gearMode\":1}"))
    assertNull(GearModeRead.fromJson(" ".repeat(16385)))
  }
  @Test fun bothReportedParkAndBrakeRequired() {
    assertTrue(GearModeRead.parked(1,3))
    for (gear in listOf(null,0,2,3,4,5)) assertFalse(GearModeRead.parked(gear,3))
    for (brake in listOf(null,0,1,2,4)) assertFalse(GearModeRead.parked(1,brake))
  }
}

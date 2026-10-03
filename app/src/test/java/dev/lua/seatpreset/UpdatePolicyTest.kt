package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class UpdatePolicyTest {
  @Test fun `updates require explicit opt in unlocked storage and a new boot`() {
    assertFalse(UpdatePolicy.shouldCheck(false, true, 2, 1, 100, null))
    assertFalse(UpdatePolicy.shouldCheck(true, false, 2, 1, 100, null))
    assertFalse(UpdatePolicy.shouldCheck(true, true, 2, 2, 100, null))
    assertTrue(UpdatePolicy.shouldCheck(true, true, 2, 1, 100, null))
  }
  @Test fun `failed checks wait for another natural callback and clock resets are handled`() {
    assertFalse(UpdatePolicy.shouldCheck(true, true, 2, 1, 1000, 900))
    assertTrue(UpdatePolicy.shouldCheck(true, true, 2, 1, 60900, 900))
    assertTrue(UpdatePolicy.shouldCheck(true, true, 2, 1, 100, 900))
  }
  @Test fun `shell success needs both exit status and exact confirmation`() {
    assertTrue(UpdatePolicy.installSucceeded(0, "Success\n"))
    assertFalse(UpdatePolicy.installSucceeded(1, "Success"))
    assertFalse(UpdatePolicy.installSucceeded(0, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"))
    assertFalse(UpdatePolicy.installSucceeded(0, "not Success"))
  }
}

package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class MotionOperationGateTest {
  @Test fun installationCannotReplaceAnAppHoldingASignalSession() {
    val gate=MotionOperationGate();assertTrue(gate.beginMotion());assertFalse(gate.beginInstall());assertFalse(gate.beginMotion())
    gate.endMotion();assertTrue(gate.beginInstall());assertFalse(gate.beginMotion());gate.endInstall();assertTrue(gate.beginMotion())
  }
  @Test fun gatesDoNotRetainRequestsForLater() {
    val gate=MotionOperationGate();assertTrue(gate.beginInstall());assertFalse(gate.beginMotion());gate.endInstall()
    assertFalse(gate.moving());assertTrue(gate.beginMotion());gate.endMotion();assertFalse(gate.moving())
  }
}

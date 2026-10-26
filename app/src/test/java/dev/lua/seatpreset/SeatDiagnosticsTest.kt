package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class SeatDiagnosticsTest {
  @Test fun preservesRawCoordinatesAndUnverifiedUnits() {
    val position=Position(linkedMapOf("height" to 0.0,"horizontal" to 63.0,"backrest" to 48.5),"fixture","unknown-format")
    val text=SeatDiagnostics.position("saved",position)
    assertTrue(text.contains("height=0.0"));assertTrue(text.contains("backrest=48.5"))
    assertTrue(text.contains("source=fixture"));assertTrue(text.contains("format=unknown-format"))
    assertTrue(text.contains("units=unverified"))
    assertFalse(text.contains("percent"));assertFalse(text.contains("freshness=verified"))
  }
  @Test fun missingCoordinatesAreNotFabricatedAsZero() {
    assertEquals("saved unavailable",SeatDiagnostics.position("saved",null))
  }
  @Test fun reflectionFailuresRetainRootCauseAndStayBounded() {
    val error=java.lang.reflect.InvocationTargetException(SecurityException("requires GEARBOX_COMMON\n"+"x".repeat(300)))
    val text=SeatDiagnostics.failure(error)
    assertTrue(text.contains("InvocationTargetException"));assertTrue(text.contains("SecurityException:requires GEARBOX_COMMON"))
    assertFalse(text.contains("\n"));assertTrue(text.length<400)
  }
}

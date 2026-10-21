package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class GetterReadinessTest {
  @Test fun coldConnectionRetriesOnlyReadsAndReturnsActualValue() {
    var reads=0;var pauses=0
    assertEquals(42,GetterReadiness.read({pauses++}) { reads++;if(reads<3) throw java.lang.reflect.InvocationTargetException(IllegalStateException("not connected"));42 })
    assertEquals(3,reads);assertEquals(2,pauses)
  }
  @Test fun persistentConnectionFailureIsBounded() {
    var reads=0;var pauses=0
    assertTrue(runCatching { GetterReadiness.read({pauses++}) { reads++;error("not connected") } }.isFailure)
    assertEquals(5,reads);assertEquals(4,pauses)
  }
  @Test fun permissionAndOtherFailuresAreNotRetriedOrConvertedIntoCoordinates() {
    var reads=0
    assertTrue(runCatching { GetterReadiness.read({fail("Must not pause")}) { reads++;throw SecurityException("Denied") } }.isFailure)
    assertEquals(1,reads)
    assertEquals(0,GetterReadiness.read({fail("Zero is an observation, not a connection error")}) { 0 })
  }
}

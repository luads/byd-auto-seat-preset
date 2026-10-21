package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class ManualSeatRecallTest {
  private fun p(x:Double=0.0,b:Double=74.0,h:Double=40.0)=Position(mapOf("horizontal" to x,"backrest" to b,"height" to h),"test",SeatObservation.FORMAT)
  @Test fun missingForeAftDoesNotBlockKnownSavedAxes() {
    val plan=ManualSeatRecall.plan(p(),p(b=72.0,h=45.0))
    assertEquals(listOf("backrest","height"),plan.map { it.name });assertEquals(listOf(72,45),plan.map { it.to })
  }
  @Test fun validForeAftIsIncludedWithoutCalibration() { assertEquals(3,ManualSeatRecall.plan(p(40.0),p(50.0,b=72.0,h=45.0)).size) }
  @Test fun incompatibleMissingNonFiniteAndFractionalDataFail() {
    for(target in listOf(p().copy(source="other"),p().copy(coordinates=emptyMap()),p(b=Double.NaN),p(b=74.5),p(b=100.0)))
      assertTrue(runCatching { ManualSeatRecall.plan(p(),target) }.isFailure)
  }
  @Test fun zeroIsSkippedRatherThanWrittenToEndpoint() { assertFalse(ManualSeatRecall.plan(p(40.0),p(0.0)).any { it.name=="horizontal" }) }
  @Test fun confirmationExpiresIsOneShotAndCannotSurviveCancel() {
    val c=ManualSeatRecall();c.confirm(100);c.consume(100,true)
    assertTrue(runCatching { c.consume(100,true) }.isFailure)
    c.confirm(100);assertTrue(runCatching { c.consume(2101,true) }.isFailure)
    c.confirm(100);c.cancel();assertTrue(runCatching { c.consume(100,true) }.isFailure)
    c.confirm(100);assertTrue(runCatching { c.consume(100,false) }.isFailure)
  }
}

package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class SeatSignalProfileTest {
  private val before=PositionFeedback.seatAxes.associateWith { PositionFeedback.Sample(50.0,100) }
  @Test fun numericPolarityIsDerivedWithoutChangingTargets() {
    assertEquals(1,SeatSignalProfile.infer("backrest",1,before,before+mapOf("backrest" to PositionFeedback.Sample(51.0,101))))
    assertEquals(2,SeatSignalProfile.infer("horizontal",1,before,before+mapOf("horizontal" to PositionFeedback.Sample(49.0,101))))
    assertEquals(1,SeatSignalProfile.infer("height",2,before,before+mapOf("height" to PositionFeedback.Sample(49.0,101))))
  }
  @Test fun staleFlatCoupledOrLargeChangesAreNotDirectionEvidence() {
    for(after in listOf(before,before+mapOf("backrest" to PositionFeedback.Sample(51.0,100)),before+mapOf("backrest" to PositionFeedback.Sample(75.0,101)),before+mapOf("backrest" to PositionFeedback.Sample(51.0,101),"height" to PositionFeedback.Sample(51.0,101)))) {
      assertThrows(IllegalArgumentException::class.java) { SeatSignalProfile.infer("backrest",1,before,after) }
    }
  }
}

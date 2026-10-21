package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class NativeSeatObservationTrialTest {
  private fun sample(at: Long, x: Double? = 20.0, y: Double? = 3.0) = NativeSeatObservationTrial.Sample(at, mapOf("setting.horizontal" to x, "bodywork.backrest" to y))
  @Test fun nativeChangeAndReturnProduceEvidenceWithoutEnablingAnyWriter() {
    val trial = NativeSeatObservationTrial()
    trial.add("horizontal", sample(100))
    trial.add("horizontal", sample(200, 25.0))
    val report = trial.add("horizontal", sample(300))
    assertTrue(trial.complete())
    assertTrue(report.contains("changed and returned"))
    assertTrue(report.contains("flat; cached values"))
    assertTrue(report.contains("no writer validated"))
    assertFalse(SeatWritePolicy.allowed())
  }
  @Test fun unavailableAndZeroRemainRawObservationsRatherThanTargets() {
    val trial = NativeSeatObservationTrial()
    trial.add("height", sample(100, 0.0, null))
    trial.add("height", sample(200, 0.0, 1.0))
    val report = trial.add("height", sample(300, 0.0, null))
    assertTrue(report.contains("A=0.0 B=0.0 A2=0.0: flat"))
    assertTrue(report.contains("unavailable in at least one phase"))
  }
  @Test fun incompleteReturnDoesNotValidateCoordinateContract() {
    val trial = NativeSeatObservationTrial()
    trial.add("horizontal", sample(1))
    trial.add("horizontal", sample(2, 25.0))
    val report = trial.add("horizontal", sample(3, 21.0))
    assertTrue(report.contains("changed without exact return"))
  }
  @Test fun expiredOrWrongAxisSamplesDoNotAdvanceSequence() {
    val trial = NativeSeatObservationTrial()
    trial.add("horizontal", sample(1))
    assertTrue(runCatching { trial.add("backrest", sample(2)) }.isFailure)
    assertTrue(runCatching { trial.add("horizontal", sample(600002)) }.isFailure)
    assertEquals("B: after a small native adjustment", trial.stage())
    trial.reset()
    assertFalse(trial.complete())
    assertTrue(trial.add("backrest", sample(600003)).contains("Recorded 1/3"))
  }
  @Test fun invalidTimeRouteChangeAndNonfiniteValuesAreRejected() {
    val trial = NativeSeatObservationTrial()
    trial.add("horizontal", sample(10))
    assertTrue(runCatching { trial.add("horizontal", sample(9)) }.isFailure)
    assertTrue(runCatching { trial.add("horizontal", sample(11, Double.NaN)) }.isFailure)
    assertTrue(runCatching { trial.add("horizontal", NativeSeatObservationTrial.Sample(11, mapOf("other" to 1.0))) }.isFailure)
    assertEquals("B: after a small native adjustment", trial.stage())
  }
  @Test fun completedTrialCannotAcceptAnotherSampleUntilExplicitReset() {
    val trial = NativeSeatObservationTrial()
    repeat(3) { trial.add("horizontal", sample(it.toLong())) }
    assertTrue(runCatching { trial.add("horizontal", sample(4)) }.isFailure)
    trial.reset()
    assertTrue(trial.add("height", sample(5)).contains("Recorded 1/3"))
  }
}

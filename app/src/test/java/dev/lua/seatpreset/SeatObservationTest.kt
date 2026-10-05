package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class SeatObservationTest {
  private fun position(values: Map<String, Double> = mapOf("horizontal" to 40.0, "backrest" to 50.0, "height" to 60.0)) = Position(values, "byd-setting-driver-1:test", SeatObservation.FORMAT)
  @Test fun allFallbackZerosCannotBecomeUsableReadings() { assertFalse(SeatObservation.usable(position().coordinates.mapValues { 0.0 })); assertTrue(SeatObservation.usable(position().coordinates + ("horizontal" to 0.0))) }
  @Test fun missingInvalidOrUnsupportedAxesRejected() {
    for (v in listOf(position().coordinates - "height", position().coordinates + ("extra" to 1.0), position().coordinates + ("height" to Double.NaN), position().coordinates + ("height" to -1.0), position().coordinates + ("height" to 101.0))) assertFalse(SeatObservation.usable(v))
  }
  @Test fun changingOrDifferentFirmwareReadingsNotRepeatable() { assertFalse(SeatObservation.repeatable(position(), position().copy(source="other"))); assertFalse(SeatObservation.repeatable(position(), position(position().coordinates + ("horizontal" to 41.0)))); assertFalse(SeatObservation.repeatable(position(), position().copy(coordinateFormat="simulation-v1"))) }
  @Test fun savedObservationRoundTripsWithoutAuthorizingRecall() {
    val doc = PresetCodec.defaults().let { it.copy(presets=it.presets.mapIndexed { i,p -> if(i==0) p.copy(position=position(),capturedAtMs=1) else p }) }
    assertEquals(doc, PresetCodec.decode(PresetCodec.encode(doc))); assertFalse(DemoCoordinates.accepts(position()))
    var applied = false
    val vehicle = object: VehicleAdapter {
      override val source=position().source; override val coordinateFormat=SeatObservation.FORMAT; override val recallValidated=false
      override fun snapshot()=VehicleSnapshot(Gear.P,true,0.0,100)
      override fun capture()=position(); override fun apply(position:Position):Boolean { applied=true;return true }
    }
    assertFalse(RecallController(vehicle){100}.recall(doc.presets.first()).allowed); assertFalse(applied)
  }
  @Test fun planNamesExactDriverSetterCandidatesButDoesNotExecute() { val plan=SeatObservation.plan(position(),position()); assertTrue(plan.contains("turnSeatHorizationPercent")); assertTrue(plan.contains("turnSeatbackrestPercent"));assertTrue(plan.contains("driver area 1")) }
  @Test fun incompatibleTargetCannotProduceAPlan() { assertTrue(runCatching { SeatObservation.plan(position(),position().copy(source="other")) }.isFailure) }
}

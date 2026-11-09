package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class FeedbackProbeTest {
  private fun sample(at:Long=1100,value:Double?=50.0,route:String="NATIVE.setting",began:Long=1000)=FeedbackProbe.Sample(route,value,began,at)
  @Test fun fixedRoutesAndRawValuesRemainDiagnosticsOnly() {
    val p=FeedbackProbe("horizontal",1000)
    assertTrue(p.accept(sample(value=0.0)))
    assertTrue(p.accept(sample(at=1200,value=1.0)))
    assertTrue(p.summary().contains("1 changes"))
    assertTrue(p.summary().contains("last raw 1.0"))
    assertFalse(FeedbackProbe.liveMovementAllowed())
    assertTrue(p.summary().contains("No movement path is unlocked"))
    assertFalse(p.accept(sample(route="unknown")))
  }
  @Test fun expiredCancelledAndBackwardsSamplesAreDiscarded() {
    val p=FeedbackProbe("height",1000)
    assertFalse(p.accept(sample(at=11000)))
    assertFalse(p.accept(sample(at=999)))
    assertFalse(p.accept(sample(at=1200,began=1300)))
    p.stop();assertFalse(p.accept(sample()));assertFalse(p.open(1001))
  }
  @Test fun malformedNumbersAreNeverReportedAsAvailableValues() {
    val p=FeedbackProbe("backrest",1000)
    for(v in listOf(null,Double.NaN,Double.POSITIVE_INFINITY,-999999999.0,101.0))assertTrue(p.accept(sample(value=v)))
    assertTrue(p.summary().contains("5 invalid"));assertTrue(p.summary().contains("last raw unavailable"))
  }
  @Test fun samplesAndRoutesStayBoundedWithoutAQueuedRetry() {
    val p=FeedbackProbe("height",1000)
    repeat(FeedbackProbe.MAX_SAMPLES) { assertTrue(p.accept(sample())) }
    assertFalse(p.accept(sample()));assertFalse(p.open(1100))
  }
  @Test fun summariesKeepRouteLatencySeparateFromCacheFreshness() {
    val p=FeedbackProbe("backrest",1000)
    assertTrue(p.accept(FeedbackProbe.Sample("typed-adb",50.0,1000,1300,100L)))
    assertTrue(p.accept(sample(at=1005)))
    assertTrue(p.summary().contains("typed-adb: 1 reads"));assertTrue(p.summary().contains("median 300 ms"))
    assertTrue(p.summary().contains("median 5 ms"));assertFalse(FeedbackProbe.liveMovementAllowed())
  }
  @Test fun delayedPostReleaseUpdatesKeepTheirObservationTimes() {
    val p=PostReleaseTrace("height",PositionFeedback.Sample(39.0,1),1000)
    assertTrue(p.observe(mapOf("height" to PositionFeedback.Sample(40.0,2)),1200).contains("sinceReleaseMs=200"))
    val late=p.observe(mapOf("height" to PositionFeedback.Sample(41.0,3)),1400)
    assertTrue(late.contains("changedSincePrevious=true"));assertTrue(late.contains("deltaSinceBefore=2.0"));assertTrue(late.contains("physicalStop=unverified"))
    assertTrue(p.observe(emptyMap(),1500).contains("accepted=false"))
    assertTrue(p.observe(mapOf("height" to PositionFeedback.Sample(42.0,4)),1300).contains("accepted=false"))
  }
}

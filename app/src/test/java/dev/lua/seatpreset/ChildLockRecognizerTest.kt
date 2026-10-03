package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class ChildLockRecognizerTest {
  private fun events(samples: List<ChildSample>): List<ChildSide> { val r = ChildLockRecognizer(); return samples.mapNotNull(r::sample) }
  @Test fun replayMeasuredPairsBySide() {
    listOf(100L, 302L, 303L, 605L).forEach { gap ->
      ChildSide.values().forEach { side -> assertEquals(listOf(side), events(ChildReplay.samples(side, listOf(gap)))) }
    }
  }
  @Test fun replayRetainsReportedRawStateOrder() {
    assertEquals(listOf(2,1,2), ChildReplay.samples(ChildSide.LEFT,listOf(100)).map { it.left })
    assertEquals(listOf(1,2,1), ChildReplay.samples(ChildSide.RIGHT,listOf(100)).map { it.right })
  }
  @Test fun repeatedPairsAreConsumedOnce() { assertEquals(listOf(ChildSide.RIGHT, ChildSide.RIGHT), events(ChildReplay.samples(ChildSide.RIGHT, listOf(300, 605)))) }
  @Test fun singleEdgeAndHeldValueDoNotEmit() { assertTrue(events(listOf(ChildSample(0,1,2), ChildSample(100,2,2), ChildSample(200,2,2))).isEmpty()) }
  @Test fun timedOutPairDoesNotBecomeAnotherGesture() { assertTrue(events(ChildReplay.samples(ChildSide.LEFT,listOf(1_001))).isEmpty()) }
  @Test fun missingInvalidAndSamplingGapBreakThePair() {
    listOf(ChildSample(200,null,2),ChildSample(200,0,2),ChildSample(200,2,0),ChildSample(400,2,2)).forEach { bad ->
      assertTrue(events(listOf(ChildSample(0,1,2),ChildSample(100,2,2),bad,ChildSample(bad.atMs+100,1,2))).isEmpty())
    }
  }
  @Test fun ambiguousSidesAndOppositeSideChangesDoNotEmit() {
    assertTrue(events(listOf(ChildSample(0,1,2),ChildSample(100,2,1),ChildSample(200,1,2))).isEmpty())
    assertTrue(events(listOf(ChildSample(0,1,2),ChildSample(100,2,2),ChildSample(200,2,1),ChildSample(300,1,2))).isEmpty())
  }
  @Test fun restartAndBadClocksClearPending() {
    val r=ChildLockRecognizer();r.sample(ChildSample(0,1,2));r.sample(ChildSample(100,2,2));r.reset();assertNull(r.sample(ChildSample(200,1,2)))
    listOf(-1L,100L,50L,Long.MIN_VALUE).forEach { at -> assertTrue(events(listOf(ChildSample(0,1,2),ChildSample(100,2,2),ChildSample(at,2,2),ChildSample(200,1,2))).isEmpty()) }
  }
}

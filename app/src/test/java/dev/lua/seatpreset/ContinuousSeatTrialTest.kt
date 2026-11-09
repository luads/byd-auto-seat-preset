package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class ContinuousSeatTrialTest {
  private class Rig:ContinuousSeatTrial.Port {
    var at=1000L;var value=50.0;var held=0;var eligible=true;var rate=.25;var age=0L;var pauseExtra=0L;var readExtra=0L;var startThrows=false;var cancel=false
    val calls=mutableListOf<Int>();val heldReads=mutableListOf<Int>()
    override fun clock()=at
    override fun pause(ms:Long) { at+=ms+pauseExtra;if(held!=0)value+=rate*(if(held==1) 1 else -1); if(cancel)eligible=false }
    override fun eligible()=eligible
    override fun latest():ContinuousSeatTrial.Sample { at+=readExtra;heldReads+=held;return ContinuousSeatTrial.Sample(value,at-age) }
    override fun signal(direction:Int) { calls+=direction;held=direction;if(direction!=0 && startThrows)error("uncertain start") }
  }
  @Test fun simulationUsesOneStartAndOneReleaseWithFeedbackWhileHeld() {
    val p=Rig();ContinuousSeatTrial.simulate(p,52.0)
    assertEquals(listOf(1,0),p.calls);assertTrue(p.heldReads.count { it==1 }>1);assertTrue(kotlin.math.abs(p.value-52.0)<=FeedbackSeatRecall.TOLERANCE)
  }
  @Test fun alreadyMatchedDoesNotStartOrRelease() {
    val p=Rig();ContinuousSeatTrial.simulate(p,50.0);assertTrue(p.calls.isEmpty())
  }
  @Test fun invalidTargetsOrInitialStaleFeedbackNeverStart() {
    for(target in listOf(Double.NaN,101.0,-1.0)) {
      val p=Rig();assertThrows(IllegalArgumentException::class.java) { ContinuousSeatTrial.simulate(p,target) };assertTrue(p.calls.isEmpty())
    }
    val p=Rig();p.age=151;assertThrows(IllegalStateException::class.java) { ContinuousSeatTrial.simulate(p,52.0) };assertTrue(p.calls.isEmpty())
  }
  @Test fun cancellationOvershootWrongDirectionAndFlatFeedbackReleaseWithoutRetry() {
    for(mode in 0..3) {
      val p=Rig();if(mode==0)p.cancel=true;if(mode==1)p.rate=4.0;if(mode==2)p.rate=-1.0;if(mode==3)p.rate=0.0
      assertThrows(IllegalStateException::class.java) { ContinuousSeatTrial.simulate(p,52.0) }
      assertEquals(listOf(1,0),p.calls)
    }
  }
  @Test fun uncertainStartAndSlowPauseStillRelease() {
    val p=Rig();p.startThrows=true
    assertThrows(IllegalStateException::class.java) { ContinuousSeatTrial.simulate(p,52.0) };assertEquals(listOf(1,0),p.calls)
    val slow=Rig();slow.pauseExtra=1000
    assertThrows(IllegalStateException::class.java) { ContinuousSeatTrial.simulate(slow,52.0) };assertEquals(listOf(1,0),slow.calls)
  }
  @Test fun fixedHoldLimitCannotBeExtendedByProgress() {
    val p=Rig();p.rate=.01
    assertThrows(IllegalStateException::class.java) { ContinuousSeatTrial.simulate(p,90.0) }
    assertEquals(listOf(1,0),p.calls);assertEquals(2000L,p.at)
  }
  @Test fun decreasingTargetUsesOneOppositeStartAndRelease() {
    val p=Rig();ContinuousSeatTrial.simulate(p,48.0)
    assertEquals(listOf(2,0),p.calls);assertTrue(kotlin.math.abs(p.value-48.0)<=FeedbackSeatRecall.TOLERANCE)
  }

}

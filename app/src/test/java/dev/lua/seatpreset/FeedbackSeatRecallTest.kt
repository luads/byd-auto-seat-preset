package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class FeedbackSeatRecallTest {
  private class Rig:FeedbackSeatRecall.Port {
    var at=1_000L;var on=true;var visible=true;var gear=Gear.P;var brake:Boolean?=true
    var values=PositionFeedback.seatAxes.associateWith { PositionFeedback.Sample(50.0,100L) }
    val calls=mutableListOf<Pair<String,Int>>()
    val callTimes=mutableListOf<Long>()
    var increasing=1
    override fun increasingSignal(axis:String)=increasing
    var releaseThrows=false
    var noFeedback=false;var reverse=false;var coupled=false;var overshoot=false;var startThrows=false;var cancelOnStart=false;var callback:(()->Unit)?=null
    override fun clock()=at
    override fun pause(ms:Long) { at+=ms;pauses+=ms;pauseHook?.invoke() }
    override fun enabled()=on
    override fun active()=visible
    var vehicleHook:(()->Unit)?=null
    override fun vehicle():VehicleSnapshot { vehicleHook?.invoke();return VehicleSnapshot(gear,brake,null,at) }
    val audits=mutableListOf<String>()
    val pauses=mutableListOf<Long>()
    var pauseHook:(()->Unit)?=null
    var readHook:(()->Unit)?=null
    override fun read():Map<String,PositionFeedback.Sample> { readHook?.invoke();return values.toMap() }
    override fun audit(detail:String) { audits+=detail }
    override fun signal(axis:String,direction:Int) {
      calls+=axis to direction;callTimes+=at
      if(direction==0) { if(releaseThrows)error("uncertain release");return }
      if(startThrows) error("uncertain start")
      if(!noFeedback) {
        val old=values.getValue(axis)
        val delta=(if(direction==1) 1 else -1)*(if(reverse) -1 else 1)*(if(overshoot) 4 else 1)
        values=values+mapOf(axis to PositionFeedback.Sample(old.value+delta,old.cacheAtNs+1))
        if(coupled) values=values+mapOf("height" to PositionFeedback.Sample(51.0,101L))
      }
      if(cancelOnStart) visible=false
      callback?.invoke()
    }
  }
  @Test fun loadsIndependentTargetsBySignalsAndAlwaysReleases() {
    val r=Rig();val result=FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0,"height" to 49.0),1000)
    assertEquals(3,result.pulses)
    assertEquals(listOf("backrest" to 1,"backrest" to 0,"backrest" to 1,"backrest" to 0,"height" to 2,"height" to 0),r.calls)
  }
  @Test fun invalidOrExpiredOrUnparkedRequestNeverStarts() {
    for(mode in 0..4) {
      val r=Rig();if(mode==0)r.gear=Gear.D;if(mode==1)r.brake=null;if(mode==2)r.on=false;if(mode==3)r.visible=false
      assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 51.0),if(mode==4) -1 else 1000) }
      assertTrue(r.calls.isEmpty())
    }
  }
  @Test fun confirmedOppositePolarityCanLoadWithoutAutomaticReversal() {
    val r=Rig();r.increasing=2;r.reverse=true
    assertEquals(1,FeedbackSeatRecall.run(r,mapOf("backrest" to 51.0),1000).pulses)
    assertEquals(listOf("backrest" to 2,"backrest" to 0),r.calls)
  }
  @Test fun uncertainStartStillAttemptsReleaseWithoutRetry() {
    val r=Rig();r.startThrows=true
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 51.0),1000) }
    assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
  }
  @Test fun noAdvancingFeedbackWrongDirectionCouplingAndOvershootStopAfterOnePulse() {
    for(mode in 0..3) {
      val r=Rig();r.noFeedback=mode==0;r.reverse=mode==1;r.coupled=mode==2;r.overshoot=mode==3
      assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0),1000) }
      assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
    }
  }
  @Test fun cancellationOrGearLossOrOptOutStillReleasesAndStops() {
    for(mode in 0..2) {
      val r=Rig();r.callback={ if(mode==0)r.visible=false;if(mode==1)r.gear=Gear.D;if(mode==2)r.on=false }
      assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 53.0),1000) }
      assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
    }
  }
  @Test fun pulseLimitIsFiniteAndNoRequestIsStored() {
    val r=Rig()
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 99.0),1000) }
    assertEquals(FeedbackSeatRecall.MAX_AXIS_PULSES*2,r.calls.size)
    assertEquals(0,r.calls.last().second)
    val second=Rig();assertEquals(0,FeedbackSeatRecall.run(second,mapOf("backrest" to 50.0),1000).pulses)
  }
  @Test fun blockingReadCannotAcceptLateFeedbackOrLostEligibility() {
    for(mode in 0..2) {
      val r=Rig();var reads=0
      r.readHook={ reads++;if(reads==2) { if(mode==0) r.at+=FeedbackSeatRecall.FEEDBACK_WAIT_MS+1 else if(mode==1) r.gear=Gear.D else r.visible=false } }
      assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 51.0),1000) }
      assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
    }
  }
  @Test fun finalReadRechecksEligibilityEvenWhenNoMovementIsNeeded() {
    val r=Rig();var reads=0;r.readHook={ reads++;if(reads==2)r.gear=Gear.D }
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 50.0),1000) }
    assertTrue(r.calls.isEmpty())
  }
  @Test fun coarseHoldsAreBoundedAndCancellationIsCheckedWithinTheHold() {
    val r=Rig();r.pauseHook={ r.visible=false }
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 70.0),1000) }
    assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
    assertEquals(listOf(25L),r.pauses)
    assertTrue(r.audits.any { it.contains("pulseMs=300 mode=coarse-to-fine") })
  }
  @Test fun AdvancingTimestampWithFlatQuantizedValueStillCannotAuthorizeAnotherSignal() {
    val r=Rig();r.noFeedback=true
    r.readHook={ if(r.calls.isNotEmpty()) { val old=r.values.getValue("backrest");r.values=r.values+("backrest" to old.copy(cacheAtNs=old.cacheAtNs+1)) } }
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 70.0),1000) }
    assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
  }
  @Test fun releaseFailureNeverStartsAnotherPulse() {
    val r=Rig();r.releaseThrows=true
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 70.0),1000) }
    assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
    assertTrue(r.audits.any { it.contains("returned=false") })
  }
  @Test fun unchangedFeedbackNeverCausesABlindLargerRetry() {
    val r=Rig();r.noFeedback=true
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 70.0),1000) }
    assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
    assertTrue(r.audits.any { it.contains("readback axis=backrest") && it.contains("changed=false") })
  }
  @Test fun unknownOrNonFiniteTargetsCannotDispatch() {
    for(target in listOf(mapOf("mirror.left.x" to 50.0),mapOf("height" to Double.NaN),mapOf("height" to 101.0))) {
      val r=Rig();assertThrows(IllegalArgumentException::class.java) { FeedbackSeatRecall.run(r,target,1000) };assertTrue(r.calls.isEmpty())
    }
  }
  @Test fun selectedMinimumGapIncludesFeedbackTimeAndCrossesAxisBoundaries() {
    for(gap in SeatCommandPause.choices) {
      val r=Rig()
      FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0,"height" to 51.0),1000,gap)
      assertEquals(6,r.calls.size)
      for(index in listOf(2,4)) assertTrue(r.callTimes[index]-r.callTimes[index-1]>=gap)
      assertEquals(1000L,r.callTimes[0]) // First command is not delayed.
      assertTrue(r.audits.any { it.contains("minimumPauseMs=$gap") })
    }
    val r=Rig();r.readHook={ r.at+=600 }
    FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0),1000,500)
    assertTrue(r.pauses.all { it<=25 })
    assertEquals(200L,r.pauses.sum()) // Existing feedback work already exceeds the minimum.
  }
  @Test fun cancellationAndParkLossDuringPauseNeverSendTheNextCommand() {
    for(mode in 0..2) {
      val r=Rig();r.pauseHook={ if(r.calls.size==2) { if(mode==0)r.visible=false else if(mode==1)r.gear=Gear.D else r.on=false } }
      assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0),1000,500) }
      assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
      assertTrue(r.pauses.all { it<=25 })
    }
  }
  @Test fun movementDuringAddedPauseStopsWithoutAnotherCommand() {
    val r=Rig();var reads=0
    r.readHook={ reads++;if(reads==3) r.values=r.values+("height" to PositionFeedback.Sample(51.0,102L)) }
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0),1000,500) }
    assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
  }
  @Test fun minimumPauseCannotRetryFlatFeedbackOrAcceptUnsupportedValues() {
    val r=Rig();r.noFeedback=true
    assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0),1000,500) }
    assertEquals(2,r.calls.size)
    for(gap in listOf(-1L,10L,2000L)) {
      val invalid=Rig()
      assertThrows(IllegalArgumentException::class.java) { FeedbackSeatRecall.run(invalid,mapOf("backrest" to 51.0),1000,gap) }
      assertTrue(invalid.calls.isEmpty())
    }
  }

  @Test fun slowGuardCannotRequestANegativePause() {
    val r=Rig();var guards=0
    r.vehicleHook={ if(r.calls.size==2) { guards++;if(guards==5)r.at+=600 } }
    FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0),1000,500)
    assertTrue(r.pauses.all { it in 1L..25L })
    assertTrue(r.callTimes[2]-r.callTimes[1]>=500)
  }
  @Test fun alreadyMatchedTargetDoesNotWaitOrDispatch() {
    val r=Rig()
    assertEquals(0,FeedbackSeatRecall.run(r,mapOf("backrest" to 50.0),1000,1000).pulses)
    assertTrue(r.pauses.isEmpty());assertTrue(r.calls.isEmpty())
  }

  @Test fun symmetricOvershootIsNotReportedAsADirectionError() {
    val r=Rig();r.overshoot=true
    val error=assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 52.0),1000) }
    assertTrue(error.message.orEmpty().contains("passed its target"));assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
  }
  @Test fun delayedDriftAfterTargetIsLoggedWithoutCorrection() {
    val r=Rig();var reads=0
    r.readHook={ reads++;if(reads==3) r.values=r.values+("backrest" to PositionFeedback.Sample(52.0,102L)) }
    val error=assertThrows(IllegalStateException::class.java) { FeedbackSeatRecall.run(r,mapOf("backrest" to 51.0),1000) }
    assertTrue(error.message.orEmpty().contains("changed after matching"))
    assertTrue(r.audits.any { it.startsWith("final-feedback-drift") });assertEquals(listOf("backrest" to 1,"backrest" to 0),r.calls)
  }

}

package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class FeedbackSeatRecallTest {
  private class Rig:FeedbackSeatRecall.Port {
    var at=1_000L;var on=true;var visible=true;var gear=Gear.P;var brake:Boolean?=true
    var values=PositionFeedback.seatAxes.associateWith { PositionFeedback.Sample(50.0,100L) }
    val calls=mutableListOf<Pair<String,Int>>()
    var increasing=1
    override fun increasingSignal(axis:String)=increasing
    var noFeedback=false;var reverse=false;var coupled=false;var overshoot=false;var startThrows=false;var cancelOnStart=false;var callback:(()->Unit)?=null
    override fun clock()=at
    override fun pause(ms:Long) { at+=ms }
    override fun enabled()=on
    override fun active()=visible
    override fun vehicle()=VehicleSnapshot(gear,brake,null,at)
    var readHook:(()->Unit)?=null
    override fun read():Map<String,PositionFeedback.Sample> { readHook?.invoke();return values.toMap() }
    override fun audit(detail:String) {}
    override fun signal(axis:String,direction:Int) {
      calls+=axis to direction
      if(direction==0) return
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
  @Test fun unknownOrNonFiniteTargetsCannotDispatch() {
    for(target in listOf(mapOf("mirror.left.x" to 50.0),mapOf("height" to Double.NaN),mapOf("height" to 101.0))) {
      val r=Rig();assertThrows(IllegalArgumentException::class.java) { FeedbackSeatRecall.run(r,target,1000) };assertTrue(r.calls.isEmpty())
    }
  }
}

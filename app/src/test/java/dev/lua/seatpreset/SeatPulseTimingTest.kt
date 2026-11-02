package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class SeatPulseTimingTest {
  @Test fun longerHoldsOnlyWhenFarFromTarget() {
    assertEquals(100L,SeatPulseTiming.duration(1.0,.02,2.0))
    assertEquals(100L,SeatPulseTiming.duration(4.0,.02,2.0))
    assertEquals(150L,SeatPulseTiming.duration(8.0,.02,2.0))
    assertEquals(300L,SeatPulseTiming.duration(40.0,.02,2.0))
  }
  @Test fun fasterObservedMotionOrLargerStepsReduceTheHold() {
    assertTrue(SeatPulseTiming.duration(12.0,.05,2.0)<SeatPulseTiming.duration(12.0,.02,2.0))
    assertEquals(100L,SeatPulseTiming.duration(8.0,.02,6.0))
  }
  @Test fun coarseToFineUsesFewerStopsAndNeverReadsFeedbackWhileHeld() {
    var now=1000L;var held:Int?=null;var coordinate=50.0;var stamp=100L;var largestHold=0L;var accumulated=0L
    val port=object:FeedbackSeatRecall.Port {
      override fun clock()=now
      override fun enabled()=true
      override fun active()=true
      override fun vehicle()=VehicleSnapshot(Gear.P,true,null,now)
      override fun pause(ms:Long) { now+=ms;if(held!=null) { coordinate+=(if(held==1) 1 else -1)*ms*.01;stamp++;accumulated+=ms } }
      override fun read():Map<String,PositionFeedback.Sample> {
        assertNull("No blocking feedback read may hold the motor",held)
        now+=200
        return PositionFeedback.seatAxes.associateWith { PositionFeedback.Sample(if(it=="horizontal") kotlin.math.floor(coordinate) else 50.0,if(it=="horizontal") stamp else 100L) }
      }
      override fun signal(axis:String,direction:Int) { if(direction==0) { largestHold=maxOf(largestHold,accumulated);accumulated=0;held=null } else held=direction }
      override fun audit(detail:String) {}
    }
    val result=FeedbackSeatRecall.run(port,mapOf("horizontal" to 70.0),1000)
    assertTrue(result.pulses<20)
    assertTrue(largestHold in 101L..300L)
    assertNull(held)
  }
  @Test fun boundsAndInvalidEvidence() {
    for(distance in 0..100) assertTrue(SeatPulseTiming.duration(distance.toDouble(),.02,2.0) in 100L..300L)
    for(rate in listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY)) assertThrows(IllegalArgumentException::class.java) { SeatPulseTiming.duration(10.0,rate,2.0) }
  }
}

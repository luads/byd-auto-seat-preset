package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class BackrestTestPermitTest {
  private fun park(at:Long=1000)=VehicleSnapshot(Gear.P,true,null,at)
  @Test fun oneConfirmedUnitCanBeConsumedOnlyOnce() {
    val permit=BackrestTestPermit.issue(50.0,1,1000)
    assertEquals(51,permit.consume(1100,50.0,park(),true,true))
    assertThrows(IllegalStateException::class.java) { permit.consume(1100,50.0,park(),true,true) }
  }
  @Test fun rangeAndShapeRejectEndpointsZerosFractionsAndLargerCommands() {
    for(value in listOf(0.0,1.0,2.0,98.0,99.0,Double.NaN,50.5))
      assertThrows(IllegalArgumentException::class.java) { BackrestTestPermit.issue(value,1,1000) }
    for(delta in listOf(-2,0,2,100)) assertThrows(IllegalArgumentException::class.java) { BackrestTestPermit.issue(50.0,delta,1000) }
    assertEquals(2,BackrestTestPermit.issue(3.0,-1,1000).consume(1100,3.0,park(),true,true))
    assertEquals(98,BackrestTestPermit.issue(97.0,1,1000).consume(1100,97.0,park(),true,true))
  }
  @Test fun unknownUnparkedStaleOrMovingStatesNeverDispatch() {
    for(state in listOf(park().copy(gear=Gear.D),park().copy(gear=null),park().copy(parkingBrake=false),park().copy(parkingBrake=null),park(-2000),park(2000),park().copy(speedKph=1.0),park().copy(speedKph=Double.NaN))) {
      val permit=BackrestTestPermit.issue(50.0,1,1000)
      assertThrows(IllegalStateException::class.java) { permit.consume(1100,50.0,state,true,true) }
      assertThrows(IllegalStateException::class.java) { permit.consume(1100,50.0,park(),true,true) }
    }
  }
  @Test fun delayedChangedOrCancelledRequestsFailAndCannotReplay() {
    for(mode in 0..4) {
      val permit=BackrestTestPermit.issue(50.0,-1,1000)
      assertThrows(IllegalStateException::class.java) {
        permit.consume(if(mode==0) 3001 else if(mode==4) 999 else 1100,if(mode==1) 51.0 else 50.0,park(),mode!=2,mode!=3)
      }
      assertThrows(IllegalStateException::class.java) { permit.consume(1100,50.0,park(),true,true) }
    }
  }
}

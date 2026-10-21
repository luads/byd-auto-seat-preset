package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class SeatCommandGuardTest {
  private val axes=listOf(ManualSeatRecall.Axis("backrest",74,72),ManualSeatRecall.Axis("height",40,42))
  private fun p(at:Long=100)=VehicleSnapshot(Gear.P,true,null,at)
  @Test fun enabledParkedRequestWritesEachAxisOnce() {
    val sent=mutableListOf<String>();SeatCommandGuard.dispatch(axes,100,{100},{true},{p()},{},{ sent+=it.name });assertEquals(listOf("backrest","height"),sent)
  }
  @Test fun disabledOrNonParkBrakeUnknownNeverWrites() {
    for(state in listOf(p().copy(gear=Gear.D),p().copy(parkingBrake=null),p().copy(parkingBrake=false),p().copy(sampledAtMs=null))) {
      var count=0;assertTrue(runCatching { SeatCommandGuard.dispatch(axes,100,{100},{true},{state},{},{count++}) }.isFailure);assertEquals(0,count)
    }
    var count=0;assertTrue(runCatching { SeatCommandGuard.dispatch(axes,100,{100},{false},{p()},{},{count++}) }.isFailure);assertEquals(0,count)
  }
  @Test fun expiryOrDisableDuringPreparationCannotSendAnAxis() {
    var at=100L;var sent=0
    assertTrue(runCatching { SeatCommandGuard.dispatch(axes,100,{at},{true},{p(at)},{at=2101},{sent++}) }.isFailure);assertEquals(0,sent)
    var on=true
    assertTrue(runCatching { SeatCommandGuard.dispatch(axes,100,{100},{on},{p()},{on=false},{sent++}) }.isFailure);assertEquals(0,sent)
  }
  @Test fun stateChangeAfterFirstAxisStopsRemainingAxes() {
    var on=true;var count=0
    assertTrue(runCatching { SeatCommandGuard.dispatch(axes,100,{100},{on},{p()},{},{count++;on=false}) }.isFailure);assertEquals(1,count)
  }
  @Test fun expiredRequestAndInvalidClockCannotBecomeFreshByStartingLater() {
    for(pair in listOf(100L to 2101L,100L to 99L,-1L to 100L)) {
      var count=0;assertTrue(runCatching { SeatCommandGuard.dispatch(axes,pair.first,{pair.second},{true},{p(pair.second)},{},{count++}) }.isFailure);assertEquals(0,count)
    }
  }
}

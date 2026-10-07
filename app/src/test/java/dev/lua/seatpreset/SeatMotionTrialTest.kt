package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class SeatMotionTrialTest {
  private fun p(x:Double=40.0)=Position(mapOf("horizontal" to x,"backrest" to 50.0,"height" to 60.0),"byd-setting-driver-1:test",SeatObservation.FORMAT)
  private fun verified()=SeatMotionTrial().apply { record(p(),0);record(p(42.0),1);record(p(),2) }
  private fun parked(t:Long=100)=VehicleSnapshot(Gear.P,true,0.0,t)
  @Test fun initialOrConstantSamplesNeverAuthorizeMovement() { val r=SeatMotionTrial();r.record(p(),0);assertFalse(r.validated("horizontal"));r.record(p(),1);r.record(p(),2);assertFalse(r.validated("horizontal")) }
  @Test fun onlyObservedAxisAndNativeRangeCanBePrepared() { val r=verified();assertTrue(r.validated("horizontal"));assertFalse(r.validated("height"));for(target in listOf(p(),p(43.0),p(40.5),p(0.0))) assertTrue(runCatching { r.prepare("horizontal",p(),target,100) }.isFailure) }
  @Test fun mixedAxesAndFirmwareFailCalibration() { val r=SeatMotionTrial();r.record(p(),0);r.record(p(42.0).copy(coordinates=p(42.0).coordinates+("height" to 61.0)),1);r.record(p(),2);assertFalse(r.validated("horizontal")) }
  @Test fun consentIsOneShotAndExpires() { val r=verified();r.prepare("horizontal",p(),p(42.0),100);assertEquals(42,r.consume(100,parked(),true).target);assertTrue(runCatching { r.consume(100,parked(),true) }.isFailure);r.prepare("horizontal",p(),p(42.0),100);assertTrue(runCatching { r.consume(2101,parked(2101),true) }.isFailure);assertTrue(runCatching { r.consume(100,parked(),true) }.isFailure) }
  @Test fun foregroundParkBrakeSpeedAndStaleEvidenceRemainRequired() { for(s in listOf(VehicleSnapshot(Gear.D,true,0.0,100),VehicleSnapshot(Gear.P,false,0.0,100),VehicleSnapshot(Gear.P,true,1.0,100),VehicleSnapshot(Gear.P,true,0.0,null))) { val r=verified();r.prepare("horizontal",p(),p(42.0),100);assertTrue(runCatching { r.consume(100,s,true) }.isFailure) };val r=verified();r.prepare("horizontal",p(),p(42.0),100);assertTrue(runCatching { r.consume(100,parked(),false) }.isFailure) }
  @Test fun invalidReadDiscardsCalibrationAndPendingConsent() { val r=verified();r.prepare("horizontal",p(),p(42.0),100);assertTrue(runCatching { r.record(p().copy(coordinates=emptyMap()),101) }.isFailure);assertFalse(r.validated("horizontal"));assertTrue(runCatching { r.consume(100,parked(),true) }.isFailure) }
  @Test fun leavingScreenAndOldCalibrationCannotBeReplayed() { val r=verified();r.prepare("horizontal",p(),p(42.0),100);r.reset();assertTrue(runCatching { r.consume(100,parked(),true) }.isFailure);assertTrue(runCatching { verified().prepare("horizontal",p(),p(42.0),300003) }.isFailure) }
  @Test fun widerNativeRangeAllowsSavedTargetButNeverExtrapolates() {
    val r=SeatMotionTrial().apply { record(p(40.0),0);record(p(50.0),1);record(p(40.0),2) }
    assertEquals(50,r.prepare("horizontal",p(40.0),p(50.0),100).target)
    assertTrue(runCatching { r.prepare("horizontal",p(39.0),p(50.0),100) }.isFailure)
    assertTrue(runCatching { r.prepare("horizontal",p(40.0),p(51.0),100) }.isFailure)
  }

}

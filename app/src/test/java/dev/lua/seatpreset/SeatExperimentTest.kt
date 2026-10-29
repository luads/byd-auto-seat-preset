package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class SeatExperimentTest {
  private val base=SeatExperiment.axes.associateWith { 50.0 as Double? }
  private fun park(at:Long=1000)=VehicleSnapshot(Gear.P,true,null,at)
  @Test fun singleAndBatchHaveDistinctPayloadsWithoutMirrorsOrSteering() {
    val one=SeatExperiment.payload(SeatExperimentMethod.SINGLE,base,-1)
    val all=SeatExperiment.payload(SeatExperimentMethod.BATCH,base,-1)
    assertEquals(mapOf("backrest" to 49f),one)
    assertEquals(7,all.size)
    assertEquals(49f,all["backrest"])
    all.filterKeys { it!="backrest" }.values.forEach { assertEquals(50f,it) }
    assertArrayEquals(intArrayOf(0x4C115018),SeatExperiment.ids(one))
    assertTrue(SeatExperiment.ids(all).all { it in 0x4C115010..0x4C115040 })
  }
  @Test fun retiredMethodCollectorAndUnknownGeometryCannotWrite() {
    assertThrows(IllegalArgumentException::class.java) { SeatExperiment.payload(SeatExperimentMethod.LEGACY,base,1) }
    assertThrows(IllegalArgumentException::class.java) { SeatExperimentPermit.issue(SeatExperimentMethod.SINGLE,SeatSdkSource.COLLECTOR,base,1,1000) }
    for(value in listOf(null,0.0,127.5,Double.NaN,Double.POSITIVE_INFINITY,-1.0)) {
      assertThrows(IllegalArgumentException::class.java) { SeatExperiment.payload(SeatExperimentMethod.SINGLE,base+mapOf("backrest" to value),1) }
      assertThrows(IllegalArgumentException::class.java) { SeatExperiment.payload(SeatExperimentMethod.BATCH,base+mapOf("height" to value),1) }
    }
    assertEquals(1,SeatExperiment.payload(SeatExperimentMethod.SINGLE,base+mapOf("height" to null),1).size)
  }
  @Test fun unknownOrKnownIntegerDispatcherCannotWrite() {
    assertThrows(IllegalStateException::class.java) { SeatExperiment.requireBackend(emptyList()) }
    assertThrows(IllegalStateException::class.java) { SeatExperiment.requireBackend(listOf("oem.Manager","android.hardware.bydauto.AbsTsMyCar")) }
    SeatExperiment.requireBackend(listOf("oem.NativeGeometryManager"))
  }
  @Test fun payloadCannotBeMutatedAfterConfirmation() {
    val permit=SeatExperimentPermit.issue(SeatExperimentMethod.BATCH,SeatSdkSource.NATIVE,base,1,1000)
    assertThrows(UnsupportedOperationException::class.java) { (permit.values as MutableMap)["backrest"]=0f }
  }
  @Test fun batchRejectsChangedPreservedAxesAndCannotReplay() {
    val permit=SeatExperimentPermit.issue(SeatExperimentMethod.BATCH,SeatSdkSource.NATIVE,base,-1,1000)
    assertThrows(IllegalStateException::class.java) { permit.consume(1100,base+mapOf("height" to 51.0),park(),true,true,SeatSdkSource.NATIVE) }
    assertThrows(IllegalStateException::class.java) { permit.consume(1100,base,park(),true,true,SeatSdkSource.NATIVE) }
  }
  @Test fun singlePermitRejectsExpiryBackgroundDisableSourceChangesAndBadPark() {
    for(mode in 0..6) {
      val permit=SeatExperimentPermit.issue(SeatExperimentMethod.SINGLE,SeatSdkSource.NATIVE,base,1,1000)
      assertThrows(IllegalStateException::class.java) {
        permit.consume(if(mode==0) 3001 else 1100,if(mode==6) base+mapOf("backrest" to 51.0) else base,
          if(mode==4) park().copy(gear=Gear.D) else if(mode==5) park().copy(parkingBrake=null) else park(),mode!=1,mode!=2,
          if(mode==3) SeatSdkSource.COLLECTOR else SeatSdkSource.NATIVE)
      }
    }
  }
  @Test fun oneExplicitCommandIsNotRepeatedByConsumption() {
    val permit=SeatExperimentPermit.issue(SeatExperimentMethod.SINGLE,SeatSdkSource.NATIVE,base,-1,1000)
    assertEquals(mapOf("backrest" to 49f),permit.consume(1100,base,park(),true,true,SeatSdkSource.NATIVE))
    assertThrows(IllegalStateException::class.java) { permit.consume(1100,base,park(),true,true,SeatSdkSource.NATIVE) }
  }
  @Test fun experimentAccessIsOwnPackageOnlyAndReadSetupDoesNotGainSet() {
    assertEquals("pm grant dev.lua.seatpreset android.permission.BYDAUTO_BODYWORK_SET",SeatExperimentAccess.grantCommand("dev.lua.seatpreset"))
    assertThrows(IllegalArgumentException::class.java) { SeatExperimentAccess.grantCommand("other.app") }
    assertFalse(VehicleReadPermissions.required.contains(SeatExperimentAccess.WRITE))
  }
  @Test fun structuredPayloadSurvivesPrivacyFilter() {
    val text=SeatExperiment.describe(SeatExperimentMethod.BATCH,SeatSdkSource.NATIVE,SeatExperiment.payload(SeatExperimentMethod.BATCH,base,1))
    assertEquals(text,DiagnosticTransferPolicy.redact(text).log)
  }
}

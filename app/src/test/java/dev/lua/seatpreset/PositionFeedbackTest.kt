package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class PositionFeedbackTest {
  private fun line(id:String,value:String="50.0",stamp:Long=100,status:Int=0,zone:String="0x0")="event count:1, lastEvent:Property:$id,status: $status,timestamp:$stamp,zone:$zone,floatValues: [$value],int32Values: [],int64Values: [],bytes: [],string:"
  private fun frame()=PositionFeedback.properties.values.joinToString("\n") { line(it) }
  @Test fun readsSevenTypedRoutesWithoutPromotingFreshness() {
    val result=PositionFeedback.parse(frame());assertEquals(7,result.size);assertEquals(100L,result.getValue("height").cacheAtNs)
    assertEquals(50.0,result.getValue("mirror.left.x").value,0.0)
  }
  @Test fun typedZeroIsRetainedButMissingBadStatusSentinelsAndWrongAreaAreNotInvented() {
    assertEquals(0.0,PositionFeedback.parse(frame().replace("50.0","0.0")).getValue("height").value,0.0)
    for(bad in listOf(line("0x21603b84","127.5"),line("0x21603b84",status=1),line("0x21603b84",zone="0x1"),line("0x21603b84",stamp=0),line("0x21603b84","NaN"))) {
      val text=frame().lineSequence().filterNot { "0x21603b84" in it }.joinToString("\n")+"\n"+bad
      assertThrows(IllegalArgumentException::class.java) { PositionFeedback.parse(text) }
    }
  }
  @Test fun duplicateAndOversizedFramesReject() {
    assertThrows(IllegalArgumentException::class.java) { PositionFeedback.parse(frame()+"\n"+line("0x21603b80")) }
    assertThrows(IllegalArgumentException::class.java) { PositionFeedback.parse("x".repeat(17000)) }
  }
  @Test fun mirrorsAreOptionalAndNeverMovementTargets() {
    val raw=PositionFeedback.seatAxes.joinToString("\n") { line(PositionFeedback.properties.getValue(it)) }
    assertEquals(3,PositionFeedback.parse(raw).size)
  }
  @Test fun legacyOrUnprovenCoordinatesAreNotAcceptedForLoading() {
    val values=PositionFeedback.properties.keys.associateWith { 50.0 }
    val evidence=values.keys.associateWith { CoordinateEvidence(PositionFeedback.route(it),1000,100) }
    val valid=Position(values,"fixture",PositionFeedback.FORMAT,evidence)
    assertTrue(PositionFeedback.accepts(valid))
    assertFalse(PositionFeedback.accepts(valid.copy(evidence=emptyMap())))
    assertFalse(PositionFeedback.accepts(valid.copy(coordinateFormat="byd-setting-driver-1-unverified-v1")))
  }
  @Test fun optionalPrivateRealCaptureUsesTheSameParser() {
    val path=System.getenv("SEAT_CAPTURE_FEEDBACK") ?: return
    val values=PositionFeedback.parse(java.io.File(path).readText())
    assertTrue(PositionFeedback.seatAxes.all { it in values })
    assertEquals(7,values.size)
    println("Private seven-axis capture parsed successfully")
  }
  @Test fun retainedMotionMirrorAndInventoryLogsFitTheLabEnvelope() {
    val inventory="SDK method=getRearviewMirrorX\n".repeat(1200)
    val movement="trial axis=backrest from=50.0 target=51.0 signal=1 release=0 returned=true\n".repeat(1500)
    val mirrors="mirror-read sdk=NATIVE axis=mirror.left.x raw=50.0 freshness=unverified\n".repeat(500)
    val raw=inventory+movement+mirrors+"latest-action-finished"
    val encoded=DiagnosticTransferPolicy.encode("dev.lua.seatpreset","0.1.18",1000,raw,emptyList())
    assertTrue(encoded.size<=DiagnosticTransferPolicy.MAX_BYTES)
    val result=org.json.JSONObject(encoded.toString(Charsets.UTF_8)).getString("log")
    assertTrue(result.contains("SDK method=getRearviewMirrorX"))
    assertTrue(result.contains("signal=1 release=0 returned=true"))
    assertTrue(result.contains("mirror-read sdk=NATIVE"))
    assertTrue(result.endsWith("latest-action-finished"))
  }
  @Test fun nativeSetupCannotGrantAnotherPackageOrChangeReadOnlySetup() {
    assertEquals("pm grant dev.lua.seatpreset android.permission.BYDAUTO_SETTING_SET",NativeSeatAccess.grantCommand("dev.lua.seatpreset"))
    assertThrows(IllegalArgumentException::class.java) { NativeSeatAccess.grantCommand("another.package") }
    assertFalse(VehicleReadPermissions.required.contains(NativeSeatAccess.PERMISSION))
  }
  @Test fun mirrorComparisonIsLimitedToFixedGetterRoutes() {
    assertEquals(4,MirrorReadProbe.routes.size)
    assertEquals(setOf("getRearviewMirrorX","getRearviewMirrorY"),MirrorReadProbe.routes.map { it.method }.toSet())
    assertTrue(MirrorReadProbe.routes.all { it.area in 1..2 })
  }
  @Test fun logsRemainShareableWithoutRelaxingIdentityPrivacy() {
    val trace="prepare axis=backrest from=50.0 target=51.0 signal=1 release=0 pulseMs=100 cacheAtNs=100"
    assertEquals(trace,DiagnosticTransferPolicy.redact(trace).log)
    assertEquals("",DiagnosticTransferPolicy.redact("driverName=private").log)
  }
}

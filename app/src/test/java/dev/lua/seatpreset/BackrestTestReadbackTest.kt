package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class BackrestTestReadbackTest {
  @Test fun coupledChangesAndMatchedTargetRemainUnverified() {
    val result=BackrestTestReadback.describe(mapOf("setting.backrest" to 50.0,"setting.height" to 40.0),mapOf("setting.backrest" to 51.0,"setting.height" to 0.0),51.0)
    assertTrue(result.contains("targetReported=true"))
    assertTrue(result.contains("otherChangedRoutes=[setting.height]"))
    assertTrue(result.contains("delta=-40.0"))
    assertTrue(result.contains("physicalCompletion=unverified"))
    assertEquals(result,DiagnosticTransferPolicy.redact(result).log)
  }
  @Test fun missingReadingsAreNotInventedAsZeroOrMatched() {
    val result=BackrestTestReadback.describe(mapOf("setting.backrest" to 50.0),mapOf("setting.backrest" to null),51.0)
    assertTrue(result.contains("targetReported=false"));assertTrue(result.contains("delta=unknown"))
  }
}

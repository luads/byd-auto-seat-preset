package dev.lua.seatpreset

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagnosticTransferPolicyTest {
  @Test fun privacyFilterOmitsSecretsAndIdentityButKeepsSeatEvidence() {
    val vinShape = "A".repeat(8) + "1".repeat(9)
    val log = """2026-10-22 presetId=a-1 raw={1=12,2=40}
2026-10-22 driverName=Private driver
2026-10-22 failure VIN=$vinShape
2026-10-22 unrelated $vinShape text
2026-10-22 token=credential
2026-10-22 adbkey=private
2026-10-22 keyId=012345
2026-10-22 phone=123456
2026-10-22 device=aa:bb:cc:dd:ee:ff
2026-10-22 rawGearMode=1 rawEPB=1"""
    val result = DiagnosticTransferPolicy.redact(log)
    assertTrue(result.log.contains("presetId=a-1 raw={1=12,2=40}"))
    assertTrue(result.log.contains("rawGearMode=1 rawEPB=1"))
    assertFalse(result.log.contains("Private driver"))
    assertFalse(result.log.contains(vinShape))
    assertFalse(result.log.contains("credential"))
    assertFalse(result.log.contains("aa:bb"))
    assertTrue(result.warnings.single().contains("8 diagnostic line(s)"))
  }
  @Test fun warningCountAndIndividualLengthFitReceiverContract() {
    val bytes = DiagnosticTransferPolicy.encode("dev.lua.seatpreset", "test", 1L, "log", List(50) { "x".repeat(2000) })
    val notes = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("warnings")
    assertTrue(notes.length() <= 32)
    repeat(notes.length()) { assertTrue(notes.getString(it).length <= 1024) }
    assertTrue(notes.toString().contains("bounded for export"))
  }
  @Test fun emptyAndMismatchedTrustNeverPass() {
    val cert = "ab".repeat(32)
    assertFalse(DiagnosticTransferPolicy.trusted("", listOf(cert)))
    assertFalse(DiagnosticTransferPolicy.trusted(cert, listOf("cd".repeat(32))))
    assertFalse(DiagnosticTransferPolicy.trusted(cert, listOf(cert, cert)))
    assertTrue(DiagnosticTransferPolicy.trusted(cert.uppercase().chunked(2).joinToString(":"), listOf(cert)))
  }
  @Test fun contractContainsOnlyBoundedDiagnosticsAndLossWarnings() {
    val log = (1..2000).joinToString("\n") { "2026-10-22 trace=sample raw=${"座".repeat(100)}" }
    val bytes = DiagnosticTransferPolicy.encode("dev.lua.seatpreset", "0.1.test", 123L, log, listOf("Earlier history unavailable"))
    assertTrue(bytes.size <= DiagnosticTransferPolicy.MAX_BYTES)
    val json = JSONObject(bytes.toString(Charsets.UTF_8))
    assertEquals(setOf("schema","sourcePackage","sourceVersion","capturedAtMs","warnings","log"), json.keySet())
    assertEquals(1, json.getInt("schema"))
    assertEquals(123L, json.getLong("capturedAtMs"))
    assertTrue(json.getJSONArray("warnings").toString().contains("oldest diagnostic lines omitted"))
    assertTrue(log.endsWith(json.getString("log")))
  }
  @Test fun tokenIsRandomUuidPathOnlyAndExpirationFailsClosed() {
    val token = java.util.UUID.randomUUID().toString()
    assertTrue(DiagnosticTransferPolicy.validToken(token))
    assertFalse(DiagnosticTransferPolicy.validToken("../private-observations.log"))
    assertFalse(DiagnosticTransferPolicy.validToken("$token/extra"))
    assertTrue(DiagnosticTransferPolicy.fresh(100, 100))
    assertFalse(DiagnosticTransferPolicy.fresh(100, 99))
    assertFalse(DiagnosticTransferPolicy.fresh(100, 300100))
  }
  @Test fun failedFlushRemainsInMemoryWithDropEvidence() {
    val buffer = DeferredLogBuffer(maxLines=1, maxChars=100)
    buffer.append("first timestamp", { error("storage locked") })
    buffer.append("second timestamp", { error("storage locked") })
    assertEquals(1, buffer.status().pendingLines)
    assertEquals(1, buffer.status().droppedLines)
    var text=""
    assertTrue(buffer.flush { text=it })
    assertTrue(text.contains("1 early line(s) dropped"))
    assertTrue(text.contains("second timestamp"))
    assertEquals(0, buffer.status().pendingLines)
  }
}

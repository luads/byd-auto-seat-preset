package dev.lua.seatpreset

import org.json.JSONArray
import org.json.JSONObject

/** Narrow wire contract. No presets, credentials or SDK are read by the encoder. */
internal object DiagnosticTransferPolicy {
  const val LAB_PACKAGE = "dev.lua.adblab"
  const val MIME = "application/vnd.dev.lua.seatpreset.diagnostics+json"
  const val MAX_BYTES = 262_144
  const val LIFETIME_MS = 300_000L
  fun fingerprint(value: String): String {
    val normalized = value.filterNot { it == ':' || it.isWhitespace() }.lowercase()
    require(Regex("[0-9a-f]{64}").matches(normalized)) { "Enter the trusted Lab SHA-256 certificate, 64 hexadecimal digits" }
    return normalized
  }
  fun trusted(expected: String, installed: List<String>): Boolean = runCatching {
    installed.size == 1 && fingerprint(expected) == fingerprint(installed.single())
  }.getOrDefault(false)
  fun validToken(value: String): Boolean = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(value)
  fun fresh(created: Long, now: Long): Boolean = now >= created && now - created < LIFETIME_MS
  data class Redacted(val log: String, val warnings: List<String>)
  fun redact(log: String): Redacted {
    // Whole suspicious lines are omitted, rather than leaking adjacent values from free-form errors.
    val sensitiveLabel = Regex("(?:^|[^A-Za-z])(?:vin|driverName|presetName|name|token|password|secret|authorization|preferences?|adbkey|private.?key|keyId|keyNo|keySerial|userPhone|phone|accountId|deviceId)\\s*[=:]", RegexOption.IGNORE_CASE)
    val vin = Regex("(?<![A-Za-z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
    val mac = Regex("(?<![A-Za-z0-9])(?:[A-Fa-f0-9]{2}:){5}[A-Fa-f0-9]{2}(?![A-Za-z0-9])")
    var omitted = 0
    var pem = false
    val bearer = Regex("\\bBearer\\s+", RegexOption.IGNORE_CASE)
    val safe = log.lineSequence().filter { line ->
      if (line.contains("-----BEGIN ")) pem = true
      val secret = pem || sensitiveLabel.containsMatchIn(line) || vin.containsMatchIn(line) ||
        mac.containsMatchIn(line) || bearer.containsMatchIn(line) || '\u0000' in line
      if (line.contains("-----END ")) pem = false
      if (secret) omitted++
      !secret
    }.joinToString("\n")
    return Redacted(safe, if (omitted > 0) listOf("Privacy filter omitted $omitted diagnostic line(s) containing sensitive labels, VIN-shaped values, MAC addresses or key material.") else emptyList())
  }
  fun encode(sourcePackage: String, sourceVersion: String, capturedAt: Long, log: String, warnings: List<String>): ByteArray {
    require(sourcePackage == "dev.lua.seatpreset")
    require(sourceVersion.matches(Regex("[A-Za-z0-9._+()-]{1,80}")) && capturedAt > 0)
    val filtered = redact(log)
    var retained = filtered.log
    val originalNotes = filtered.warnings + warnings
    val notes = originalNotes.take(30).map { it.replace("\u0000", "[NUL]").take(1024) }.toMutableList()
    if (originalNotes.size > 30 || originalNotes.any { it.length > 1024 }) notes += "Some warning text was bounded for export; this is not a complete warning history."
    fun bytes() = JSONObject().put("schema", 1).put("sourcePackage", sourcePackage)
      .put("sourceVersion", sourceVersion).put("capturedAtMs", capturedAt)
      .put("warnings", JSONArray(notes)).put("log", retained).toString().toByteArray(Charsets.UTF_8)
    var encoded = bytes()
    if (encoded.size > MAX_BYTES) {
      notes += "Export byte limit reached; oldest diagnostic lines omitted. This is not a complete history."
      val safe = filtered.log
      val starts = listOf(0) + safe.indices.filter { safe[it] == '\n' }.map { it + 1 } + safe.length
      var low = 0
      var high = starts.lastIndex
      while (low < high) {
        val middle = (low + high) / 2
        retained = safe.substring(starts[middle])
        if (bytes().size > MAX_BYTES) low = middle + 1 else high = middle
      }
      retained = safe.substring(starts[low])
      encoded = bytes()
    }
    require(notes.size <= 32 && encoded.size <= MAX_BYTES) { "Diagnostic envelope exceeds contract limit" }
    return encoded
  }
}

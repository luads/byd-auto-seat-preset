package dev.lua.seatpreset

import org.json.JSONArray
import org.json.JSONObject

data class PresetDocument(val presets: List<Preset>, val favourites: List<String>)
object PresetCodec {
  const val MAX_BYTES = 65_536
  fun defaults() = PresetDocument(listOf(Preset("driver-one", "Driver 1"), Preset("driver-two", "Driver 2")), listOf("driver-one", "driver-two"))
  fun validate(doc: PresetDocument) {
    require(doc.presets.size in 2..32) { "Keep between 2 and 32 presets" }
    require(doc.presets.map { it.id }.distinct().size == doc.presets.size) { "Duplicate preset identifiers" }
    doc.presets.forEach { p ->
      require(p.id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid preset identifier" }
      require(p.name.isNotBlank() && p.name.length <= 48 && p.name.none { it.isISOControl() }) { "Invalid driver name" }
      require(p.capturedAtMs == null || p.capturedAtMs >= 0) { "Invalid capture time" }
      p.position?.let { pos ->
        require(pos.source.isNotBlank() && pos.source.length <= 80 && pos.coordinateFormat.isNotBlank() && pos.coordinateFormat.length <= 80) { "Missing position format" }
        require(pos.coordinates.size in 1..16 && pos.coordinates.all { (key, value) -> key.matches(Regex("[A-Za-z0-9_.:-]{1,64}")) && value.isFinite() }) { "Invalid position coordinates" }
      }
      require(p.position != null || p.capturedAtMs == null) { "Capture time without position" }
    }
    require(doc.favourites.size == 2 && doc.favourites.distinct().size == 2 && doc.favourites.all { id -> doc.presets.any { it.id == id } }) { "Choose two distinct existing favourites" }
  }
  fun array(presets: List<Preset>): JSONArray = JSONArray().apply {
    presets.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).apply {
      p.position?.let { pos -> put("position", JSONObject().put("source", pos.source).put("format", pos.coordinateFormat).put("coordinates", JSONObject(pos.coordinates))) }
      p.capturedAtMs?.let { put("capturedAtMs", it) }
    }) }
  }
  fun encode(doc: PresetDocument): String {
    validate(doc)
    return JSONObject().put("schema", 1).put("presets", array(doc.presets)).put("favourites", JSONArray(doc.favourites)).toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Preset file is too large" } }
  }
  private fun string(obj: JSONObject, key: String): String { val value = obj.get(key); require(value is String) { "Expected text for $key" }; return value }
  private fun parse(array: JSONArray): List<Preset> = (0 until array.length()).map { i ->
    val p = array.getJSONObject(i)
    val position = if (!p.has("position") || p.isNull("position")) null else p.getJSONObject("position").let { pos ->
      val coords = pos.getJSONObject("coordinates")
      Position(coords.keys().asSequence().associateWith { key ->
        val value = coords.get(key); require(value is Number) { "Coordinates must be numbers" }; value.toDouble()
      }, string(pos, "source"), string(pos, "format"))
    }
    val at = if (p.has("capturedAtMs")) p.get("capturedAtMs").let { value -> require(value is Long || value is Int) { "Capture time must be an integer" }; (value as Number).toLong() } else null
    Preset(string(p, "id"), string(p, "name"), position, at)
  }
  fun decode(raw: String): PresetDocument {
    require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Preset file is too large" }
    val tokens = org.json.JSONTokener(raw)
    val root = tokens.nextValue() as? JSONObject ?: throw IllegalArgumentException("Expected a preset document")
    require(tokens.nextClean() == 0.toChar()) { "Unexpected data after preset document" }
    require(root.get("schema") == 1) { "Unsupported preset file version" }
    val favs = root.getJSONArray("favourites")
    return PresetDocument(parse(root.getJSONArray("presets")), (0 until favs.length()).map { favs.get(it).let { value -> require(value is String); value } }).also(::validate)
  }
  fun migrate(raw: String?, first: String?, second: String?): PresetDocument {
    if (raw == null) return defaults()
    require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
    val all = parse(JSONArray(raw)); require(all.size >= 2)
    val a = all.firstOrNull { it.id == first } ?: all[0]
    val b = all.firstOrNull { it.id == second && it.id != a.id } ?: all.first { it.id != a.id }
    return PresetDocument(all, listOf(a.id, b.id)).also(::validate)
  }
}

interface PresetPersistence {
  fun read(): String?
  fun previous(): String?
  fun write(current: String, previous: String?)
}
data class PresetLoad(val document: PresetDocument, val recovered: Boolean = false)
class PresetRepository(private val disk: PresetPersistence, private val legacy: () -> PresetDocument) {
  fun load(): PresetLoad {
    val raw = try { disk.read() } catch (e: Exception) { return PresetLoad(PresetCodec.decode(disk.previous() ?: throw e), true) }
    if (raw == null) {
      val backup = disk.previous()
      return if (backup != null) PresetLoad(PresetCodec.decode(backup), true) else PresetLoad(legacy())
    }
    return try { PresetLoad(PresetCodec.decode(raw)) } catch (e: Exception) {
      val backup = disk.previous() ?: throw IllegalStateException("Preset data is damaged. Import a backup to recover.", e)
      PresetLoad(PresetCodec.decode(backup), true)
    }
  }
  fun save(next: PresetDocument) {
    val encoded = PresetCodec.encode(next)
    val old = PresetCodec.encode(load().document)
    disk.write(encoded, old)
  }
  // Explicit confirmed import may replace damaged data. It never calls Vehicle.
  fun restore(raw: String) {
    val next = PresetCodec.encode(PresetCodec.decode(raw))
    val old = runCatching { PresetCodec.encode(load().document) }.getOrNull()
    disk.write(next, old)
  }
}

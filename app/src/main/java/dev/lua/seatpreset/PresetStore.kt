package dev.lua.seatpreset

import android.content.Context
import android.util.AtomicFile
import java.io.File

@android.annotation.SuppressLint("ApplySharedPref")
class PresetStore(private val context: Context) {
  private val prefs get() = StorageAccess.prefs(context, "seat-presets-v1")
  private val repository by lazy { StorageAccess.requireUnlocked(context); PresetRepository(object : PresetPersistence {
    private val main = AtomicFile(File(context.filesDir, "presets.json"))
    private val backup = AtomicFile(File(context.filesDir, "presets.previous.json"))
    private fun read(file: AtomicFile): String? = try { file.openRead().use { input ->
      val buffer = java.io.ByteArrayOutputStream(); val chunk = ByteArray(4096)
      while (buffer.size() <= PresetCodec.MAX_BYTES) { val count = input.read(chunk, 0, minOf(chunk.size, PresetCodec.MAX_BYTES + 1 - buffer.size())); if (count < 0) break; buffer.write(chunk, 0, count) }
      val bytes = buffer.toByteArray(); require(bytes.size <= PresetCodec.MAX_BYTES) { "Preset file is too large" }; bytes.toString(Charsets.UTF_8)
    } } catch (e: java.io.FileNotFoundException) {
      if (java.nio.file.Files.notExists(file.baseFile.toPath())) null else throw e
    }
    override fun read() = read(main)
    override fun previous() = read(backup)
    private fun atomic(file: AtomicFile, value: String) {
      val output = file.startWrite()
      try { output.write(value.toByteArray(Charsets.UTF_8)); output.fd.sync(); file.finishWrite(output) }
      catch (e: Exception) { file.failWrite(output); throw e }
      check(read(file) == value) { "Could not verify preset save" }
    }
    override fun write(current: String, previous: String?) {
      if (previous != null) atomic(backup, previous)
      atomic(main, current)
    }
  }) { PresetCodec.migrate(prefs.getString("presets", null), prefs.getString("favourite-0", null), prefs.getString("favourite-1", null)) } }
  fun all(): List<Preset> = synchronized(lock) { StorageAccess.requireUnlocked(context); runCatching { repository.load().document.presets }.getOrElse { PresetCodec.defaults().presets } }
  fun storageMessage(): String? = synchronized(lock) { StorageAccess.requireUnlocked(context);
    runCatching { if (repository.load().recovered) "Recovered the previous saved presets. Check them before use." else null }.getOrElse { "Preset data is damaged. Import a backup before saving." }
  }
  fun save(presets: List<Preset>) = synchronized(lock) { StorageAccess.requireUnlocked(context);
    val loaded = repository.load(); check(!loaded.recovered) { "Confirm the recovered presets first" }; val old = loaded.document
    require(old.favourites.all { id -> presets.any { it.id == id } }) { "A favourite cannot be removed" }
    repository.save(old.copy(presets = presets)); mirror()
  }
  fun favourites(): List<Preset> = synchronized(lock) { StorageAccess.requireUnlocked(context);
    val doc = runCatching { repository.load().document }.getOrElse { PresetCodec.defaults() }
    doc.favourites.map { id -> doc.presets.first { it.id == id } }
  }
  fun setFavourite(slot: Int, preset: Preset) = synchronized(lock) { StorageAccess.requireUnlocked(context);
    val loaded = repository.load(); check(!loaded.recovered) { "Confirm the recovered presets first" }; val old = loaded.document
    require(old.presets.any { it.id == preset.id })
    repository.save(old.copy(favourites = FavouriteSlots.assign(old.favourites, slot, preset.id))); mirror()
  }
  fun acceptRecovery() = synchronized(lock) { StorageAccess.requireUnlocked(context); repository.save(repository.load().document); mirror() }
  fun exportDocument(): String = synchronized(lock) { StorageAccess.requireUnlocked(context); PresetCodec.encode(repository.load().document) }
  fun importDocument(raw: String) = synchronized(lock) { StorageAccess.requireUnlocked(context); repository.restore(raw); mirror() }
  private fun mirror() {
    // Compatibility copy for existing diagnostics only. The atomic document is authoritative.
    val doc = repository.load().document
    check(prefs.edit().putString("presets", PresetCodec.array(doc.presets).toString()).putString("favourite-0", doc.favourites[0]).putString("favourite-1", doc.favourites[1]).commit())
  }
  var developerMode: Boolean
    get() = prefs.getBoolean("developer-mode", false)
    set(value) { check(prefs.edit().putBoolean("developer-mode", value).commit()) }
  var result: String
    get() = prefs.getString("last-result", "Select either favourite to recall")!!
    set(value) { check(prefs.edit().putString("last-result", value).commit()) }
  var checkUpdatesOnStart: Boolean
    get() = prefs.getBoolean("check-updates", false)
    set(value) { check(prefs.edit().putBoolean("check-updates", value).commit()) }
  var startup: String
    get() = prefs.getString("startup", "Startup has not been observed")!!
    set(value) { check(prefs.edit().putString("startup", value).commit()) }
  companion object { private val lock = Any() }
}

package dev.lua.seatpreset

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.test.InstrumentationTestCase
import java.io.File
import java.io.IOException

/** Real Android preferences/files and retry worker; isolated paths, no vehicle calls. */
class StartupIntegrationTest : InstrumentationTestCase() {
  private lateinit var root: File
  private lateinit var base: Context
  override fun setUp() { super.setUp(); base = instrumentation.targetContext; root = File(base.cacheDir, "startup-qa").apply { deleteRecursively(); mkdirs() } }
  override fun tearDown() { root.deleteRecursively(); super.tearDown() }
  private fun context(unavailableUser: Boolean = false, unavailablePrefs: Boolean = false) = object : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = root
    override fun getSystemService(name: String): Any? = if (name == Context.USER_SERVICE && unavailableUser) null else super.getSystemService(name)
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
      if (unavailablePrefs) throw IOException("injected credential failure")
      return super.getSharedPreferences("qa-$name", mode)
    }
  }
  fun testUnavailableUserNeverInspectsCredentialKeysOrPresets() {
    val c = context(unavailableUser = true)
    assertFalse(StorageAccess.unlocked(c))
    assertTrue(runCatching { PresetChannel.key(c) }.isFailure)
    assertTrue(runCatching { PresetStore(c).all() }.isFailure)
    assertEquals(0, root.listFiles()!!.size)
  }
  fun testPartialAndUnreadableKeysAreNotReplaced() {
    val c = context(); val private = File(root, "panel-adbkey"); val public = File(root, "panel-adbkey.pub")
    private.writeText("existing private")
    assertTrue(runCatching { PresetChannel.key(c) }.isFailure); assertFalse(public.exists())
    public.writeText("existing public")
    assertTrue(runCatching { PresetChannel.key(c) }.isFailure)
    assertEquals("existing private", private.readText()); assertEquals("existing public", public.readText())
  }
  fun testCredentialFailureCannotCreateASavedPreset() {
    val c = context(unavailablePrefs = true); val store = PresetStore(c)
    assertNotNull(store.storageMessage()); assertTrue(store.all().all { it.position == null })
    assertTrue(runCatching { store.exportDocument() }.isFailure)
    assertFalse(File(root, "presets.json").exists())
  }
  fun testAtomicLogsRetryAndPrivateClearDoesNotLeak() {
    var writable = false
    val c = object : ContextWrapper(base) { override fun getFilesDir(): File { if (!writable) throw IOException("injected disk failure"); return root } }
    val public = CredentialLog("public.log"); val private = CredentialLog("private.log")
    public.append(c, "public event"); private.append(c, "private-only marker")
    private.clear(c); writable = true; public.flush(c); private.flush(c)
    assertTrue(File(root, "public.log").readText().contains("public event"))
    assertFalse(File(root, "public.log").readText().contains("private-only marker"))
    assertFalse(File(root, "private.log").exists())
  }
  fun testOneCredentialFailureDoesNotKillStartupWorker() {
    val c = context(unavailablePrefs = true)
    Startup.retry(c)
    val log = File(root, "widget-diagnostics.log")
    val until = android.os.SystemClock.elapsedRealtime() + 5000
    while (android.os.SystemClock.elapsedRealtime() < until && (!log.exists() || !log.readText().contains("storage deferred"))) {
      // Application.onCreate can have a pass in flight. Retry after its coalesced callback.
      Startup.retry(c); Thread.sleep(100)
    }
    assertTrue(log.exists()); assertTrue(log.readText().contains("storage deferred"))
    // Flush follows failed credential actions, proving they did not abort the pass.
    assertTrue(log.readText().contains("recovery deferred"))
  }
}

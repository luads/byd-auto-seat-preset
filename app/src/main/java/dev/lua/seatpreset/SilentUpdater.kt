package dev.lua.seatpreset

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dadb.Dadb
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Only this app's verified APK. No persisted movement request, shell input or remote endpoint. */
internal object SilentUpdater {
  private val choice = RecoveryChoice()
  private val busy = AtomicBoolean(false)
  private val worker = Executors.newSingleThreadExecutor()
  @Volatile private var checkedWithoutBoot = false
  @Volatile private var attemptedAt: Long? = null
  private fun prefs(c: Context) = StorageAccess.prefs(c, "updates")
  fun enabled(c: Context) = choice.read { prefs(c).getBoolean("automatic", false) } == true
  fun setEnabled(c: Context, value: Boolean) { choice.save(value) { prefs(c).edit().putBoolean("automatic", it).commit() } }
  fun status(c: Context) = prefs(c).getString("status", "Automatic updates are off")!!
  private fun status(c: Context, value: String) { check(prefs(c).edit().putString("status", value).commit()) }
  private fun boot(c: Context): Int? = runCatching { Settings.Global.getInt(c.contentResolver, "boot_count").takeIf { it >= 0 } }.getOrNull()
  fun resolve(c: Context) {
    val p = prefs(c); val pending = p.getLong("pending-version", -1)
    if (pending < 0) return
    if (BuildConfig.VERSION_CODE.toLong() >= pending) {
      check(p.edit().remove("pending-version").putString("status", "Updated to ${BuildConfig.VERSION_NAME}").commit())
      WidgetDiagnostics.record(c, "update replacement verified version=${BuildConfig.VERSION_CODE}")
    } else status(c, "Update did not finish. Check updates to retry.")
  }
  fun onNaturalTick(c: Context) {
    if (!StorageAccess.unlocked(c)) return
    val app = c.applicationContext
    if (busy.get()) return
    val now = SystemClock.elapsedRealtime(); val boot = boot(app)
    if (boot == null && checkedWithoutBoot) return
    val p = prefs(app)
    val completed = if (p.contains("checked-boot")) p.getInt("checked-boot", -1) else null
    if (!UpdatePolicy.shouldCheck(enabled(app), true, boot, completed, now, attemptedAt)) return
    if (!busy.compareAndSet(false, true)) return
    attemptedAt = now
    worker.execute {
      try {
        if (!enabled(app)) return@execute
        val release = PublicUpdater.latest()
        if (release == null) {
          status(app, "No newer update")
          if (boot != null) check(p.edit().putInt("checked-boot", boot).commit()) else checkedWithoutBoot = true
        } else install(app, release, requireOptIn = true)
      } catch (_: Exception) {
        runCatching { status(app, "Update deferred. Open Settings to retry.") }
        WidgetDiagnostics.record(app, "automatic update deferred")
      } finally { busy.set(false) }
    }
  }
  /** Called on a worker by an explicit tap or the opt-in natural startup path. */
  fun install(c: Context, release: PublicRelease, requireOptIn: Boolean = false) = synchronized(PublicUpdater) {
    StorageAccess.requireUnlocked(c)
    if (requireOptIn) check(enabled(c)) { "Automatic updates are off" }
    require(c.packageName in setOf("dev.lua.seatpreset", "dev.lua.seatpreset.demo"))
    val key = checkNotNull(PresetChannel.key(c, firstUse = false)) { "Complete home access setup first" }
    PublicUpdater.downloadAndVerify(c, release)
    val apk = File(c.cacheDir, "update.apk")
    val remote = "/data/local/tmp/seat-preset-${c.packageName}-update.apk"
    Dadb.create("127.0.0.1", 5555, key, connectTimeout = 2_000, socketTimeout = 20_000).use { adb ->
      if (requireOptIn) check(enabled(c)) { "Automatic updates are off" }
      status(c, "Installing verified update")
      adb.push(apk, remote, 420, apk.lastModified() / 1000)
      try {
        if (requireOptIn) check(enabled(c)) { "Automatic updates are off" }
        // Commit the marker before pm replaces and kills this process. It never stores an action.
        check(prefs(c).edit().putLong("pending-version", release.versionCode).commit()) { "Cannot record pending update" }
        val result = adb.shell("pm install -r $remote")
        check(UpdatePolicy.installSucceeded(result.exitCode, result.allOutput)) { "Android did not confirm the update" }
      } finally { runCatching { adb.shell("rm -f $remote") } }
    }
  }
}

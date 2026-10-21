package dev.lua.seatpreset

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Explicit owner transfer only. Never sends to Telegram, calls OEM APIs or starts capture. */
internal object SeatDiagnosticTransfer {
  private val preparation = DiagnosticPreparation<Intent>(java.util.concurrent.Executors.newSingleThreadExecutor { work ->
    Thread(work, "seat-diagnostic-export").apply { isDaemon = true }
  })
  /** Independent of Activity/OEM workers. A second Activity cannot queue another export. */
  fun prepareAsync(context: Context, completed: (Result<Intent>) -> Unit): Boolean {
    val app = context.applicationContext
    return preparation.submit({ prepare(app) }, { callback ->
      check(android.os.Handler(android.os.Looper.getMainLooper()).post(callback)) { "Owner UI unavailable" }
    }, completed)
  }
  private const val PREFERENCES = "diagnostic-transfer"
  private const val CERTIFICATE = "trusted-lab-sha256"
  fun certificate(context: Context): String = StorageAccess.prefs(context, PREFERENCES).getString(CERTIFICATE, "").orEmpty()
  fun trust(context: Context, value: String) {
    val normalized = if (value.isBlank()) "" else DiagnosticTransferPolicy.fingerprint(value)
    check(StorageAccess.prefs(context, PREFERENCES).edit().putString(CERTIFICATE, normalized).commit()) { "Could not save trusted certificate" }
    clear(context)
  }
  fun verifyLab(context: Context): Int {
    val pm = context.packageManager
    val info = pm.getPackageInfo(DiagnosticTransferPolicy.LAB_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
    val certificates = info.signingInfo?.apkContentsSigners.orEmpty().map { signature ->
      MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    check(DiagnosticTransferPolicy.trusted(certificate(context), certificates)) { "ADB Lab certificate is missing or does not match the trusted certificate" }
    return checkNotNull(info.applicationInfo).uid
  }
  private fun directory(context: Context) = File(context.filesDir, "diagnostic-transfer").apply {
    StorageAccess.requireUnlocked(context)
    check(mkdirs() || isDirectory) { "Diagnostic transfer storage unavailable" }
  }
  @Synchronized fun expire(context: Context) {
    directory(context).listFiles().orEmpty().forEach { file ->
      if (!DiagnosticTransferPolicy.fresh(file.lastModified(), System.currentTimeMillis())) discard(context, file)
    }
  }
  @Synchronized fun clear(context: Context) { directory(context).listFiles().orEmpty().forEach { discard(context, it) } }
  private fun uri(context: Context, token: String) = Uri.Builder().scheme("content").authority("${context.packageName}.diagnostics").appendPath("snapshot").appendPath(token).build()
  private fun discard(context: Context, file: File) {
    val token = file.name.removeSuffix(".json")
    if (DiagnosticTransferPolicy.validToken(token)) context.revokeUriPermission(uri(context, token), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    check(file.delete() || !file.exists()) { "Could not discard temporary diagnostics" }
  }
  @Synchronized fun snapshot(context: Context, incoming: Uri): File {
    StorageAccess.requireUnlocked(context)
    require(incoming.scheme == "content" && incoming.authority == "${context.packageName}.diagnostics" && incoming.query == null && incoming.fragment == null)
    require(incoming.pathSegments.size == 2 && incoming.pathSegments[0] == "snapshot")
    val token = incoming.pathSegments[1]
    require(DiagnosticTransferPolicy.validToken(token)) { "Invalid diagnostic URI" }
    expire(context)
    return File(directory(context), "$token.json").also {
      check(it.isFile && it.length() in 1..DiagnosticTransferPolicy.MAX_BYTES.toLong()) { "Snapshot unavailable or expired" }
    }
  }
  @Synchronized fun prepare(context: Context): Intent {
    StorageAccess.requireUnlocked(context)
    verifyLab(context)
    val component = ComponentName(DiagnosticTransferPolicy.LAB_PACKAGE, "dev.lua.adblab.SeatDiagnosticsImportActivity")
    val activity = context.packageManager.getActivityInfo(component, 0)
    check(activity.exported && activity.enabled && activity.applicationInfo.enabled) { "ADB Lab diagnostics import is unavailable" }
    val saved = PrivateDiagnostics.exportSnapshot(context)
    val bytes = DiagnosticTransferPolicy.encode(context.packageName, BuildConfig.VERSION_NAME, System.currentTimeMillis(), saved.log, saved.warnings)
    clear(context)
    val token = UUID.randomUUID().toString()
    val destination = File(directory(context), "$token.json")
    try {
      java.io.FileOutputStream(destination).use { it.write(bytes); it.fd.sync() }
      check(destination.length() == bytes.size.toLong())
    } catch (failure: Throwable) { destination.delete(); throw failure }
    val uri = uri(context, token)
    return Intent(Intent.ACTION_SEND).setComponent(component).setDataAndType(uri, DiagnosticTransferPolicy.MIME)
      .apply { clipData = ClipData.newUri(context.contentResolver, "Seat Presets diagnostics", uri) }
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  @Synchronized fun consumed(context: Context, incoming: Uri) { discard(context, snapshot(context, incoming)) }
}

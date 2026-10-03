package dev.lua.seatpreset

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Separate instances own separate queues and files. Never mirrors content to Logcat. */
internal class CredentialLog(private val name: String, private val limit: Int = 128_000) {
  private val queue = DeferredLogBuffer(maxChars = limit / 2)
  private var clearPending = false
  @Synchronized fun append(context: Context, message: String) {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    queue.append("$time up=${SystemClock.elapsedRealtime()} $message") { write(context, it) }
  }
  @Synchronized fun flush(context: Context) {
    if (clearPending) erase(context)
    queue.flush { write(context, it) }
  }
  @Synchronized fun clear(context: Context) {
    queue.clear(); clearPending = true
    runCatching { erase(context) }
  }
  private fun erase(context: Context) {
    StorageAccess.requireUnlocked(context)
    val file = AtomicFile(File(context.filesDir, name))
    file.delete()
    check(listOf(file.baseFile, File(context.filesDir, "$name.bak"), File(context.filesDir, "$name.new"))
      .all { java.nio.file.Files.notExists(it.toPath()) }) { "Private log deletion failed" }
    clearPending = false
  }
  private fun write(context: Context, text: String) {
    StorageAccess.requireUnlocked(context)
    if (clearPending) erase(context)
    val file = AtomicFile(File(context.filesDir, name))
    val old = try { file.openRead().use { input ->
      require(file.baseFile.length() <= limit * 2L) { "Log exceeds limit" }
      input.readBytes().toString(Charsets.UTF_8)
    } } catch (e: java.io.FileNotFoundException) {
      if (java.nio.file.Files.notExists(file.baseFile.toPath())) "" else throw e
    }
    val next = (old + text).takeLast(limit)
    val output = file.startWrite()
    try { output.write(next.toByteArray()); output.fd.sync(); file.finishWrite(output) }
    catch (e: Exception) { file.failWrite(output); throw e }
    check(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) } == next) { "Log persistence verification failed" }
  }
}

/** Future seat/identity observations belong only here, never in widget diagnostics or exports. */
internal object PrivateDiagnostics {
  private val log = CredentialLog("private-observations.log")
  fun record(context: Context, event: String) = log.append(context, event)
  fun flush(context: Context) = log.flush(context)
  fun clear(context: Context) = log.clear(context)
}

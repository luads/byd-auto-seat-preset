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
  internal data class Snapshot(val log: String, val warnings: List<String>)
  @Synchronized fun snapshot(context: Context): Snapshot {
    StorageAccess.requireUnlocked(context)
    if (clearPending) erase(context)
    val prior = queue.status()
    val flushed = queue.flush { write(context, it) }
    val warnings = mutableListOf("Bounded retained diagnostics only; earlier rotated lines and memory lost on process death cannot be recovered.", "Observation times do not prove OEM cache freshness.")
    if (prior.droppedLines > 0) warnings += "${prior.droppedLines} queued diagnostic line(s) were dropped before this snapshot."
    if (!flushed) warnings += "Flush failed: ${queue.status().pendingLines} queued line(s) remain in memory. Their persistence is uncertain; duplicates are possible on a later retry."
    val file = AtomicFile(File(context.filesDir, name))
    val text = try { file.openRead().use { input ->
      val output = java.io.ByteArrayOutputStream()
      val block = ByteArray(8192)
      while (output.size() <= limit * 4) {
        val count = input.read(block, 0, minOf(block.size, limit * 4 + 1 - output.size()))
        if (count <= 0) break
        output.write(block, 0, count)
      }
      val bytes = output.toByteArray()
      require(bytes.size <= limit * 4) { "Private log exceeds export bound" }
      bytes.toString(Charsets.UTF_8)
    } } catch (e: java.io.FileNotFoundException) {
      if (listOf(file.baseFile, File(context.filesDir, "$name.bak"), File(context.filesDir, "$name.new"))
          .all { java.nio.file.Files.notExists(it.toPath()) }) "" else throw e
    }
    if (text.contains("log-buffer:")) warnings += "Retained log contains historical buffer-loss markers."
    return Snapshot(text, warnings)
  }
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

/** Seat observations stay separate from setup logs; only explicit owner diagnostic transfer exports them. */
internal object PrivateDiagnostics {
  private val log = CredentialLog("private-observations.log")
  fun record(context: Context, event: String) = log.append(context, event)
  fun flush(context: Context) = log.flush(context)
  fun clear(context: Context) { log.clear(context); SeatDiagnosticTransfer.clear(context) }
  fun exportSnapshot(context: Context) = log.snapshot(context)
  fun report(context: Context): String {
    StorageAccess.requireUnlocked(context); log.flush(context)
    val file = File(context.filesDir, "private-observations.log")
    return if (file.exists()) file.readText().takeLast(128_000) else "No private trials recorded"
  }
}

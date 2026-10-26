package dev.lua.seatpreset

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** One bounded private inventory per firmware/SDK revision, separate from trial history. */
internal object SeatSignatureInventory {
  private const val LIMIT=48_000
  private var clearPending=false
  private const val END="\n[inventory complete]\n"
  private fun file(context:Context):AtomicFile {
    StorageAccess.requireUnlocked(context)
    return AtomicFile(File(context.filesDir,"seat-signatures.txt"))
  }
  private fun read(context:Context):String {
    val target=file(context)
    if(clearPending) erase(context)
    return try { target.openRead().use { input ->
      val output=java.io.ByteArrayOutputStream();val chunk=ByteArray(4096)
      while(output.size()<=LIMIT) { val count=input.read(chunk,0,minOf(chunk.size,LIMIT+1-output.size()));if(count<0) break;output.write(chunk,0,count) }
      val bytes=output.toByteArray();check(bytes.size<=LIMIT) { "Inventory exceeds limit" };bytes.toString(Charsets.UTF_8)
    } } catch(e:java.io.FileNotFoundException) {
      if(java.nio.file.Files.notExists(target.baseFile.toPath())) "" else throw e
    }
  }
  @Synchronized fun ensure(context:Context,load:()->String):String {
    StorageAccess.requireUnlocked(context)
    val info=context.packageManager.getPackageInfo("com.byd.data.collect",0)
    val identity="${android.os.Build.FINGERPRINT}:${info.longVersionCode}:${info.lastUpdateTime}"
    return ensureRevision(context,identity,load)
  }
  @Synchronized internal fun ensureRevision(context:Context,identity:String,load:()->String):String {
    StorageAccess.requireUnlocked(context)
    require(identity.length<=2048)
    val key=java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    val header="inventoryKey=$key\n"
    if(runCatching { val old=read(context);old.startsWith(header) && old.endsWith(END) }.getOrDefault(false)) return key
    val text=header+load().take(40_000)+END
    require(text.toByteArray(Charsets.UTF_8).size<=LIMIT) { "Inventory exceeds limit" }
    val target=file(context);val output=target.startWrite()
    try { output.write(text.toByteArray(Charsets.UTF_8));output.fd.sync();target.finishWrite(output) }
    catch(e:Exception) { target.failWrite(output);throw e }
    check(read(context)==text) { "Inventory persistence verification failed" }
    return key
  }
  @Synchronized fun snapshot(context:Context):Pair<String,List<String>> = runCatching {
    val text=read(context);check(text.isEmpty() || text.endsWith(END)) { "Incomplete SDK inventory" };text to emptyList<String>()
  }
    .getOrElse { "" to listOf("SDK inventory unavailable: ${it.javaClass.simpleName}") }
  private fun erase(context:Context) {
    val target=file(context);target.delete()
    check(listOf(target.baseFile,File(context.filesDir,"seat-signatures.txt.bak"),File(context.filesDir,"seat-signatures.txt.new"))
      .all { java.nio.file.Files.notExists(it.toPath()) }) { "Inventory clear failed" }
    clearPending=false
  }
  @Synchronized fun clear(context:Context) { clearPending=true;runCatching { erase(context) } }
}

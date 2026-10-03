package dev.lua.seatpreset

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

internal object PresetChannel {
  @Synchronized fun key(context: Context, firstUse: Boolean = true): AdbKeyPair? {
    StorageAccess.requireUnlocked(context)
    val private = File(context.filesDir, "panel-adbkey")
    val public = File(context.filesDir, "panel-adbkey.pub")
    fun present(file: File): Boolean {
      // exists() alone also returns false on access errors. Only proven absence permits generation.
      val path = file.toPath()
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
      check(Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) { "Key presence unreadable" }
      return false
    }
    val pair = present(private) to present(public)
    if (!firstUse && pair == (false to false)) return null
    return KeyStorage.load(true, { pair }, { AdbKeyPair.read(private, public) }, { AdbKeyPair.generate(private, public) })
  }
  fun authenticateExisting(context: Context): Boolean? {
    val key = key(context, firstUse = false) ?: return null
    return runCatching {
      Dadb.create("127.0.0.1", 5555, key, connectTimeout = 1_000, socketTimeout = 1_000).use {
        it.shell("echo seat-preset-ready").exitCode == 0
      }
    }.getOrDefault(false)
  }
}

package dev.lua.seatpreset

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Explicit user-selected sharing only. Contains the preset backup, never keys or logs. */
class BackupProvider : ContentProvider() {
  override fun onCreate() = true
  private fun file(uri: Uri): File {
    val c = context!!; StorageAccess.requireUnlocked(c)
    require(uri.authority == "${c.packageName}.backups" && uri.path?.matches(Regex("/backup-[0-9a-f-]{36}\\.json")) == true)
    return File(File(c.cacheDir, "backups"), uri.lastPathSegment!!)
  }
  override fun getType(uri: Uri): String { file(uri); return "application/json" }
  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    require(mode == "r"); return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
  }
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
    val f = file(uri)
    return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf("seat-presets.json", f.length())) }
  }
  override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

package dev.lua.seatpreset

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor

/** Separate from the APK provider. One use, read only, credential protected. */
class SeatDiagnosticsProvider : ContentProvider() {
  override fun onCreate(): Boolean {
    context?.let { if (StorageAccess.unlocked(it)) runCatching { SeatDiagnosticTransfer.expire(it) } }
    return true
  }
  override fun getType(uri: Uri): String {
    SeatDiagnosticTransfer.snapshot(checkNotNull(context), uri)
    return DiagnosticTransferPolicy.MIME
  }
  @Synchronized override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    require(mode == "r") { "Diagnostics are read only" }
    val app = checkNotNull(context)
    val uid = Binder.getCallingUid()
    check(uid == SeatDiagnosticTransfer.verifyLab(app)) { "Caller is not the trusted ADB Lab" }
    val file = SeatDiagnosticTransfer.snapshot(app, uri)
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    try { SeatDiagnosticTransfer.consumed(app, uri) }
    catch (failure: Throwable) { descriptor.close(); throw failure }
    // Open descriptor remains readable after unlink; a second open cannot retrieve the snapshot.
    return descriptor
  }
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

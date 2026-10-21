package dev.lua.seatpreset

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.security.MessageDigest

/** Dedicated emulator only: synthetic log and installed tokenless Lab, no vehicle or Telegram call. */
class DiagnosticTransferIntegrationTest : InstrumentationTestCase() {
  private val target get() = instrumentation.targetContext
  private fun labCertificate(): String {
    val info = target.packageManager.getPackageInfo(DiagnosticTransferPolicy.LAB_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
    val cert = checkNotNull(info.signingInfo).apkContentsSigners.single()
    return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
  }
  private fun text(node: AccessibilityNodeInfo?): String {
    if (node == null) return ""
    val value = buildString {
      append(node.text?.toString().orEmpty()); append('\n')
      repeat(node.childCount) { append(text(node.getChild(it))) }
    }
    @Suppress("DEPRECATION") node.recycle()
    return value
  }
  private fun waitFor(label: String): String {
    val deadline = android.os.SystemClock.elapsedRealtime() + 25_000
    var visible = ""
    while (android.os.SystemClock.elapsedRealtime() < deadline) {
      visible = text(instrumentation.uiAutomation.rootInActiveWindow)
      if (visible.contains(label)) return visible
      Thread.sleep(100)
    }
    fail("Expected Lab UI status: $label")
    return visible
  }
  private fun launch(intent: Intent) {
    val previous = instrumentation.uiAutomation.rootInActiveWindow?.windowId
    target.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
    val deadline = android.os.SystemClock.elapsedRealtime() + 5000
    while (android.os.SystemClock.elapsedRealtime() < deadline) {
      val window = instrumentation.uiAutomation.rootInActiveWindow?.windowId
      if (window != null && window != previous) return
      Thread.sleep(50)
    }
    fail("Expected a new Lab import activity window")
  }
  fun testMissingMismatchLockedAndReleaseSignedOneUseRoundTrip() {
    check(target.packageName == "dev.lua.seatpreset") { "Run the live variant on the dedicated emulator" }
    assertEquals(0, target.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
    val original = SeatDiagnosticTransfer.certificate(target)
    try {
      SeatDiagnosticTransfer.trust(target, "")
      assertTrue(runCatching { SeatDiagnosticTransfer.prepare(target) }.isFailure)
      SeatDiagnosticTransfer.trust(target, "00".repeat(32))
      assertTrue(runCatching { SeatDiagnosticTransfer.prepare(target) }.isFailure)
      val locked = object : ContextWrapper(target) {
        override fun getSystemService(name: String): Any? = if (name == Context.USER_SERVICE) null else super.getSystemService(name)
      }
      assertFalse(StorageAccess.unlocked(locked))
      assertTrue(runCatching { SeatDiagnosticTransfer.prepare(locked) }.isFailure)
      SeatDiagnosticTransfer.trust(target, labCertificate())
      PrivateDiagnostics.record(target, "trace=diagnostic-qa rawGearMode=1 rawEPB=1 presetId=qa-position noCommand=true")
      val outgoing = SeatDiagnosticTransfer.prepare(target)
      val uri = checkNotNull(outgoing.data)
      assertTrue(target.applicationInfo.uid != SeatDiagnosticTransfer.verifyLab(target))
      assertTrue(runCatching { target.contentResolver.openFileDescriptor(uri, "w")?.close() }.isFailure)
      assertTrue(SeatDiagnosticTransfer.snapshot(target, uri).isFile)
      val wrongUid = runCatching { target.contentResolver.openFileDescriptor(uri, "r")?.close() }.exceptionOrNull()
      assertNotNull("Seat UID must not read a snapshot intended only for Lab", wrongUid)
      assertTrue(generateSequence(wrongUid) { it.cause }.any { it.message.orEmpty().contains("Caller is not the trusted ADB Lab") })
      assertEquals(DiagnosticTransferPolicy.MIME, outgoing.type)
      assertEquals(uri, outgoing.clipData!!.getItemAt(0).uri)
      assertEquals(PackageManager.PERMISSION_DENIED, target.checkUriPermission(uri, -1, 2000, Intent.FLAG_GRANT_READ_URI_PERMISSION))
      // Non-exported provider rejects shell even while a valid snapshot exists.
      val shell = instrumentation.uiAutomation.executeShellCommand("content read --uri $uri")
      val shellText = android.os.ParcelFileDescriptor.AutoCloseInputStream(shell).use { it.bufferedReader().readText() }
      assertFalse(shellText.contains("\"schema\":1"))
      launch(outgoing)
      val visible = waitFor("Imported privately")
      assertTrue("Lab import preview did not complete", visible.contains("Imported privately"))
      assertTrue("Preview must identify source version", visible.contains(BuildConfig.VERSION_NAME))
      assertTrue("Preview must show capture provenance", visible.contains("Capture"))
      assertTrue(runCatching { SeatDiagnosticTransfer.snapshot(target, uri) }.isFailure)
      val uid = SeatDiagnosticTransfer.verifyLab(target)
      assertEquals(PackageManager.PERMISSION_DENIED, target.checkUriPermission(uri, -1, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION))
      assertTrue(File(target.filesDir, "diagnostic-transfer").listFiles().orEmpty().isEmpty())
      val goodHash = Regex("SHA-256: ([0-9a-f]{64})").find(visible)?.groupValues?.get(1)
      assertNotNull("Expected private preview checksum", goodHash)
      fun previousKept(status: String) {
        val screen = waitFor(status)
        // Retained preview loads separately; wait briefly for the same checksum if needed.
        if (!screen.contains(goodHash!!)) waitFor(goodHash)
      }
      val malformed = SeatDiagnosticTransfer.prepare(target)
      SeatDiagnosticTransfer.snapshot(target, checkNotNull(malformed.data)).writeText("{")
      launch(malformed)
      previousKept("Import failed")
      val oversize = SeatDiagnosticTransfer.prepare(target)
      SeatDiagnosticTransfer.snapshot(target, checkNotNull(oversize.data)).writeBytes(ByteArray(DiagnosticTransferPolicy.MAX_BYTES + 1))
      launch(oversize)
      previousKept("Import failed")
      val wrongMime = SeatDiagnosticTransfer.prepare(target)
      wrongMime.setDataAndType(wrongMime.data, "text/plain")
      launch(wrongMime)
      previousKept("Rejected unexpected diagnostic source")
      val wrongPath = SeatDiagnosticTransfer.prepare(target)
      val invalidUri = checkNotNull(wrongPath.data).buildUpon().path("/snapshot/not-a-uuid").build()
      wrongPath.setDataAndType(invalidUri, DiagnosticTransferPolicy.MIME)
      wrongPath.clipData = android.content.ClipData.newRawUri("Invalid synthetic path", invalidUri)
      launch(wrongPath)
      previousKept("Rejected unexpected diagnostic source")
      // Do not press Send, and never contact Telegram from this test.
    } finally {
      SeatDiagnosticTransfer.clear(target)
      SeatDiagnosticTransfer.trust(target, original)
      PrivateDiagnostics.clear(target)
    }
  }
}

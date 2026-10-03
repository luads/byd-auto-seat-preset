package dev.lua.seatpreset

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class PublicRelease(val versionName: String, val versionCode: Long, val url: String, val digest: String)

object ReleasePolicy {
  const val REPO = "luads/byd-auto-seat-preset"
  fun versionCode(name: String, flavor: String): Long? = Regex("^byd-auto-seat-preset-${Regex.escape(flavor)}-[0-9]+\\.[0-9]+\\.[0-9]+-([0-9]+)\\.apk$")
    .matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()
  fun trustedUrl(value: String): Boolean = value.startsWith("https://github.com/$REPO/releases/download/") && !value.contains("..")
}

object PublicUpdater {
  private const val MAX_APK_BYTES = 50L * 1024 * 1024
  fun latest(): PublicRelease? {
    val connection = URL("https://api.github.com/repos/${ReleasePolicy.REPO}/releases?per_page=10").openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000; connection.readTimeout = 15_000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "SeatPresets/${BuildConfig.VERSION_NAME}")
    try {
      if (connection.responseCode == 404) return null
      check(connection.responseCode == 200) { "GitHub release check failed (${connection.responseCode})" }
      val bytes = connection.inputStream.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          check(output.size() + count <= 1024 * 1024) { "Release metadata too large" }
          output.write(buffer, 0, count)
        }
        output.toByteArray()
      }
      check(bytes.size <= 1024 * 1024) { "Release metadata too large" }
      val releases = JSONArray(bytes.toString(Charsets.UTF_8))
      val candidates = (0 until releases.length()).flatMap { releaseIndex ->
      val body = releases.getJSONObject(releaseIndex)
      if (body.optBoolean("draft")) return@flatMap emptyList()
      val assets = body.getJSONArray("assets")
      (0 until assets.length()).mapNotNull { index ->
        val asset = assets.getJSONObject(index)
        val code = ReleasePolicy.versionCode(asset.getString("name"), BuildConfig.FLAVOR) ?: return@mapNotNull null
        if (code <= BuildConfig.VERSION_CODE || code > Int.MAX_VALUE) return@mapNotNull null
        val url = asset.getString("browser_download_url")
        val digest = asset.optString("digest")
        if (!ReleasePolicy.trustedUrl(url) || !Regex("sha256:[0-9a-f]{64}").matches(digest)) return@mapNotNull null
        PublicRelease(body.getString("tag_name"), code, url, digest.removePrefix("sha256:"))
      }
      }
      return candidates.maxByOrNull { it.versionCode }
    } finally { connection.disconnect() }
  }
  @Synchronized fun downloadAndVerify(context: Context, release: PublicRelease) {
    require(ReleasePolicy.trustedUrl(release.url))
    StorageAccess.requireUnlocked(context)
    val part = File(context.cacheDir, "update.part")
    val target = File(context.cacheDir, "update.apk")
    target.delete()
    val connection = URL(release.url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000; connection.readTimeout = 30_000
    try {
      check(connection.responseCode == 200) { "APK download failed (${connection.responseCode})" }
      val digest = MessageDigest.getInstance("SHA-256")
      connection.inputStream.use { input -> part.outputStream().use { output ->
        val buffer = ByteArray(8192); var total = 0L
        while (true) {
          if (Thread.currentThread().isInterrupted) throw InterruptedException()
          val count = input.read(buffer); if (count < 0) break
          total += count; check(total <= MAX_APK_BYTES) { "APK too large" }
          output.write(buffer, 0, count); digest.update(buffer, 0, count)
        }
      } }
      check(digest.digest().joinToString("") { "%02x".format(it) } == release.digest) { "APK checksum mismatch" }
      val pm = context.packageManager
      val downloaded = pm.getPackageArchiveInfo(part.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("Invalid APK")
      val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
      check(downloaded.packageName == context.packageName && downloaded.longVersionCode == release.versionCode && downloaded.longVersionCode > installed.longVersionCode) { "APK package or version mismatch" }
      val actual = downloaded.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
      val expected = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
      check(!actual.isNullOrEmpty() && actual == expected) { "APK signing key mismatch" }
      check(part.renameTo(target)) { "Could not save verified APK" }
    } finally { connection.disconnect(); part.delete() }
  }
}

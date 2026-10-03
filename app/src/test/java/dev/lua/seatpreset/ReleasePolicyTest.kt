package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
  @Test fun flavorAssetsNeverCrossInstall() {
    val name = "byd-auto-seat-preset-demo-0.1.0-2.apk"
    assertEquals(2L, ReleasePolicy.versionCode(name, "demo"))
    assertNull(ReleasePolicy.versionCode(name, "live"))
  }
  @Test fun malformedOrOverflowVersionsAreRejected() {
    listOf("byd-auto-seat-preset-live-0x1x0-2.apk", "byd-auto-seat-preset-live-0.1.0--1.apk", "byd-auto-seat-preset-live-0.1.0-9999999999999999999999.apk", "other-0.1.0-2.apk").forEach { assertNull(ReleasePolicy.versionCode(it, "live")) }
  }
  @Test fun onlyThisRepositoryReleaseAssetsAreTrusted() {
    assertTrue(ReleasePolicy.trustedUrl("https://github.com/luads/byd-auto-seat-preset/releases/download/v0.1.0/app.apk"))
    listOf("http://github.com/luads/byd-auto-seat-preset/releases/download/v1/a.apk", "https://github.com.evil/luads/byd-auto-seat-preset/releases/download/v1/a.apk", "https://github.com/other/repo/releases/download/v1/a.apk", "https://github.com/luads/byd-auto-seat-preset/releases/download/../a.apk").forEach { assertFalse(ReleasePolicy.trustedUrl(it)) }
  }
}

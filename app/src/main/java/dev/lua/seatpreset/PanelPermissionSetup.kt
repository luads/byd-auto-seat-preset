package dev.lua.seatpreset

import android.content.Context
import android.provider.Settings
import dadb.Dadb

/** Owner-triggered setup for this package only. Never called at boot or on a timer. */
object PanelPermissionSetup {
  fun enable(context: Context): String {
    StorageAccess.requireUnlocked(context)
    val beforeOverlay = Settings.canDrawOverlays(context)
    val beforeWrite = Startup.canWrite(context)
    try {
    run {
      val key = checkNotNull(PresetChannel.key(context))
      Dadb.create("127.0.0.1", 5555, key, connectTimeout = 3_000, socketTimeout = 10_000).use { adb ->
        check(adb.shell("echo seat-preset-ready").exitCode == 0) { "Setup authentication failed" }
        Startup.adbAuthenticated = true
        val own = context.packageName
        require(own in setOf("dev.lua.seatpreset", "dev.lua.seatpreset.demo"))
        if (!beforeOverlay) check(adb.shell("appops set $own SYSTEM_ALERT_WINDOW allow").exitCode == 0) { "Overlay grant failed" }
        if (!beforeWrite) check(adb.shell("pm grant $own android.permission.WRITE_SECURE_SETTINGS").exitCode == 0) { "Startup grant failed" }
      }
    }
    val afterOverlay = Settings.canDrawOverlays(context); val afterWrite = Startup.canWrite(context)
    check(afterOverlay && afterWrite) { "Permission grant could not be verified" }
    HomePanelSettings(context).setObserver(true)
    HomePanelSettings(context).enabled = true
    return "Home panel is ready. Return to home while in P."
    } finally {
      WidgetDiagnostics.record(context, "panel setup overlay=$beforeOverlay->${Settings.canDrawOverlays(context)} startup=$beforeWrite->${Startup.canWrite(context)}")
    }
  }
  fun disable(context: Context): String {
    HomePanelSettings(context).enabled = false
    HomeVisibilityService.instance?.hide()
    HomePanelSettings(context).setObserver(false)
    return "Home panel is off. Startup access remains available."
  }
}

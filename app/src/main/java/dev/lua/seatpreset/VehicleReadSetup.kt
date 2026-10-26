package dev.lua.seatpreset

import android.content.Context
import android.content.pm.PackageManager
import dadb.Dadb

/** Explicit foreground owner action. Does not enable recovery, child gestures or seat movement. */
internal object VehicleReadSetup {
  fun missing(context:Context):List<String> = VehicleReadPermissions.required.filter {
    context.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED
  }
  fun enable(context: Context): String {
    StorageAccess.requireUnlocked(context)
    context.packageManager.getApplicationInfo("com.byd.data.collect", 0)
    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    val missing = VehicleReadPermissions.required.filterNot(::granted)
    if (missing.isEmpty()) return "Vehicle read permissions are ready. You can inspect saved positions."
    val key = checkNotNull(PresetChannel.key(context))
    Dadb.create("127.0.0.1", 5555, key, connectTimeout = 3_000, socketTimeout = 10_000).use { adb ->
      check(adb.shell("echo seat-preset-read-setup").exitCode == 0) { "Setup authentication failed" }
      Startup.adbAuthenticated = true
      missing.forEach { permission ->
        runCatching { adb.shell(VehicleReadPermissions.grantCommand(context.packageName, permission)) }
          .fold({ SeatDiagnostics.event(context, "read-setup", "permission=$permission exit=${it.exitCode} granted=${granted(permission)} detail=${it.output.replace('\n',' ').take(240)}") },
            { SeatDiagnostics.event(context, "read-setup", "permission=$permission cause=" + SeatDiagnostics.failure(it)) })
      }
    }
    val remaining = VehicleReadPermissions.required.filterNot(::granted)
    return if (remaining.isEmpty()) "Vehicle read permissions are ready. You can inspect saved positions."
    else "Some readings remain unavailable: " + remaining.joinToString { it.substringAfter("BYDAUTO_") } + ". See the private report."
  }
}

package dev.lua.seatpreset

import android.content.Context
import android.content.pm.PackageManager
import dadb.Dadb

/** Explicit setup only. The read-only setup path never grants a seat-write permission. */
internal object NativeSeatAccess {
  const val PERMISSION="android.permission.BYDAUTO_SETTING_SET"
  fun grantCommand(own:String):String {
    require(own in setOf("dev.lua.seatpreset","dev.lua.seatpreset.demo"))
    return "pm grant $own $PERMISSION"
  }
  fun enable(context:Context):String {
    StorageAccess.requireUnlocked(context)
    val reads=VehicleReadSetup.enable(context)
    if(context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED) return "$reads\nNative seat-control access is already granted. No command sent."
    val key=checkNotNull(PresetChannel.key(context,firstUse=false)) { "Complete home access setup first" }
    Dadb.create("127.0.0.1",5555,key,connectTimeout=3000,socketTimeout=10000).use { channel ->
      val result=channel.shell(grantCommand(context.packageName))
      val held=context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED
      SeatDiagnostics.event(context,"seat-access","permission=SETTING_SET exit=${result.exitCode} held=$held detail=${result.output.replace('\n',' ').take(240)}")
      check(held) { "Native seat permission was not granted. See the private report." }
    }
    return "$reads\nNative seat-control access granted. No movement. Enable Seat movement separately."
  }
}

package dev.lua.seatpreset

import android.content.Context
import android.content.pm.PackageManager
import dadb.Dadb

/** A separate explicit setup action. Read setup never grants BODYWORK_SET. */
internal object SeatExperimentAccess {
  const val WRITE="android.permission.BYDAUTO_BODYWORK_SET"
  fun grantCommand(own:String):String {
    require(own in setOf("dev.lua.seatpreset","dev.lua.seatpreset.demo"))
    return "pm grant $own $WRITE"
  }
  fun enable(context:Context):String {
    StorageAccess.requireUnlocked(context)
    val reads=VehicleReadSetup.enable(context)
    if(context.checkSelfPermission(WRITE)==PackageManager.PERMISSION_GRANTED) return "$reads\nBodywork write permission is granted. No command sent."
    val key=checkNotNull(PresetChannel.key(context))
    Dadb.create("127.0.0.1",5555,key,connectTimeout=3000,socketTimeout=10000).use { adb ->
      val result=adb.shell(grantCommand(context.packageName))
      val granted=context.checkSelfPermission(WRITE)==PackageManager.PERMISSION_GRANTED
      SeatDiagnostics.event(context,"experiment-access","permission=BODYWORK_SET exit=${result.exitCode} granted=$granted detail=${result.output.replace('\n',' ').take(240)}")
      check(granted) { "BODYWORK_SET was not granted. See the private setup result." }
    }
    return "$reads\nBodywork test access granted. No command sent; each experiment still needs its own confirmation."
  }
}

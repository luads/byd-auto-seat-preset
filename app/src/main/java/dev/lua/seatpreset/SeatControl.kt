package dev.lua.seatpreset

import android.content.Context

@android.annotation.SuppressLint("ApplySharedPref")
object SeatControl {
  fun enabled(context: Context): Boolean = runCatching { StorageAccess.prefs(context,"seat-control").getBoolean("enabled",false) }.getOrDefault(false)
  fun minimumPauseMs(context:Context):Long = runCatching {
    StorageAccess.prefs(context,"seat-control").getLong("minimum-pause-ms",SeatCommandPause.DEFAULT_MS)
      .also { require(SeatCommandPause.valid(it)) }
  }.getOrDefault(SeatCommandPause.DEFAULT_MS)
  fun setMinimumPauseMs(context:Context,value:Long) {
    require(SeatCommandPause.valid(value))
    check(StorageAccess.prefs(context,"seat-control").edit().putLong("minimum-pause-ms",value).commit())
  }
  fun enable(context: Context,value: Boolean) { check(StorageAccess.prefs(context,"seat-control").edit().putBoolean("enabled",value).commit()) }
}

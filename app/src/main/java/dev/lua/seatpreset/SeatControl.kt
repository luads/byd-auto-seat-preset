package dev.lua.seatpreset

import android.content.Context

@android.annotation.SuppressLint("ApplySharedPref")
object SeatControl {
  fun enabled(context: Context): Boolean = runCatching { StorageAccess.prefs(context,"seat-control").getBoolean("enabled",false) }.getOrDefault(false)
  fun enable(context: Context,value: Boolean) { check(StorageAccess.prefs(context,"seat-control").edit().putBoolean("enabled",value).commit()) }
}

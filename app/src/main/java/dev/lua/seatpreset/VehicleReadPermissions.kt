package dev.lua.seatpreset

/** Fixed read-only setup scope. Never derives a permission or package from external input. */
internal object VehicleReadPermissions {
  val required = listOf("SETTING_COMMON", "SETTING_GET", "GEARBOX_COMMON", "GEARBOX_GET",
    "DOOR_LOCK_COMMON", "DOOR_LOCK_GET", "BODYWORK_COMMON", "BODYWORK_GET")
    .map { "android.permission.BYDAUTO_$it" }

  fun grantCommand(packageName: String, permission: String): String {
    require(packageName in setOf("dev.lua.seatpreset", "dev.lua.seatpreset.demo"))
    require(permission in required)
    return "pm grant $packageName $permission"
  }
}

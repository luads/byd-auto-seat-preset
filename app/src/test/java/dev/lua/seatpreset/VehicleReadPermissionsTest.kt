package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class VehicleReadPermissionsTest {
  @Test fun includesCommonAndGetterPermissionsWithoutWrites() {
    assertEquals(8, VehicleReadPermissions.required.size)
    assertTrue(VehicleReadPermissions.required.contains("android.permission.BYDAUTO_GEARBOX_COMMON"))
    assertTrue(VehicleReadPermissions.required.contains("android.permission.BYDAUTO_GEARBOX_GET"))
    VehicleReadPermissions.required.forEach {
      assertTrue(it.endsWith("_COMMON") || it.endsWith("_GET"))
      assertEquals("pm grant dev.lua.seatpreset $it", VehicleReadPermissions.grantCommand("dev.lua.seatpreset", it))
    }
  }
  @Test fun refusesOtherPackagesAndWritePermissions() {
    listOf("other.app", "dev.lua.seatpreset;reboot").forEach { pkg ->
      assertThrows(IllegalArgumentException::class.java) { VehicleReadPermissions.grantCommand(pkg, VehicleReadPermissions.required.first()) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      VehicleReadPermissions.grantCommand("dev.lua.seatpreset", "android.permission.BYDAUTO_SETTING_SET")
    }
  }
}

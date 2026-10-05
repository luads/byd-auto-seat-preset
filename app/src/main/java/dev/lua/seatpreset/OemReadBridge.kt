package dev.lua.seatpreset

import android.content.Context
import dalvik.system.PathClassLoader

/** Exact getter bindings, acquired only by a foreground, owner-triggered trial. */
internal class OemReadBridge(private val context: Context) {
  private val devices = mutableMapOf<String, Any>()
  private val loader by lazy {
    StorageAccess.requireUnlocked(context)
    val info = context.packageManager.getApplicationInfo("com.byd.data.collect", 0)
    PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).joinToString(":"), context.classLoader)
  }
  private fun device(kind: String): Any = devices.getOrPut(kind) {
    StorageAccess.requireUnlocked(context)
    val name = when (kind) {
      "setting" -> "android.hardware.bydauto.setting.BYDAutoSettingDevice"
      "doorlock" -> "android.hardware.bydauto.doorlock.BYDAutoDoorLockDevice"
      "gearbox" -> "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice"
      else -> error("Unsupported device")
    }
    val cls = Class.forName(name, false, loader)
    cls.getMethod("getInstance", Context::class.java).invoke(null, context.applicationContext)
      ?: error("Device is not ready")
  }
  private fun number(kind: String, name: String, area: Int? = null): Double {
    StorageAccess.requireUnlocked(context)
    require(name in setOf("getSeatHorization", "getSeatbackrestPostion", "getSeatTurnHeight", "getDoorLockStatus", "getEPBState"))
    val obj = device(kind)
    val method = if (area == null) obj.javaClass.getMethod(name) else obj.javaClass.getMethod(name, Int::class.javaPrimitiveType)
    val expected = if (name in setOf("getSeatbackrestPostion", "getSeatTurnHeight")) Float::class.javaPrimitiveType else Int::class.javaPrimitiveType
    check(method.returnType == expected) { "Getter signature changed" }
    val value = if (area == null) method.invoke(obj) else method.invoke(obj, area)
    return (value as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: error("Getter is unavailable")
  }
  fun seat(): Position {
    val values = linkedMapOf("horizontal" to number("setting", "getSeatHorization", 1),
      "backrest" to number("setting", "getSeatbackrestPostion", 1), "height" to number("setting", "getSeatTurnHeight", 1))
    PrivateDiagnostics.record(context, "raw seat getter returns at=${android.os.SystemClock.elapsedRealtime()} driverArea=1 values=$values")
    check(SeatObservation.usable(values)) { "Seat readings are unavailable or outside the candidate range" }
    Startup.times.sampled(android.os.SystemClock.elapsedRealtime())
    return Position(values, "byd-setting-driver-1:" + java.security.MessageDigest.getInstance("SHA-256").digest((android.os.Build.FINGERPRINT + ":" + context.packageManager.getPackageInfo("com.byd.data.collect", 0).longVersionCode).toByteArray()).joinToString("") { "%02x".format(it) }.take(32), SeatObservation.FORMAT)
  }
  fun children(): Pair<Int?, Int?> {
    fun read(area: Int) = runCatching { number("doorlock", "getDoorLockStatus", area).toInt().takeIf { it in 1..2 } }.getOrNull()
    return read(6) to read(7)
  }
  fun rawPark(): Pair<Int?, Int?> {
    StorageAccess.requireUnlocked(context)
    // Extract only gearMode. The setting also contains VIN data and must never be logged.
    val gear = runCatching {
      GearModeRead.fromJson(android.provider.Settings.Global.getString(context.contentResolver, GearModeRead.SETTING))
    }.getOrNull()
    val brake = runCatching { number("gearbox", "getEPBState").toInt() }.getOrNull()
    return gear to brake
  }
  fun parkedVetoClear(): Boolean {
    val raw = rawPark()
    // This checks reported state only, not cache freshness. Physical confirmation is still required.
    return GearModeRead.parked(raw.first, raw.second)
  }
  fun bindAxisWriter(axis: String): (Int) -> Unit {
    val methodName = when (axis) {
      "horizontal" -> "turnSeatHorizationPercent"
      "backrest" -> "turnSeatbackrestPercent"
      "height" -> "turnSeatHeightPercent"
      else -> error("Unsupported axis")
    }
    val obj = device("setting")
    val method = obj.javaClass.getMethod(methodName, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    check(method.returnType == Void.TYPE) { "Setter contract changed" }
    check(context.checkCallingOrSelfPermission("android.permission.BYDAUTO_SETTING_SET") == android.content.pm.PackageManager.PERMISSION_GRANTED) { "OEM seat-write permission unavailable" }
    return { value ->
      StorageAccess.requireUnlocked(context)
      require(value in 1..99)
      // Invoked only by the explicit supervised test, never by Vehicle.adapter or an input event.
      method.invoke(obj, 1, value)
    }
  }

}

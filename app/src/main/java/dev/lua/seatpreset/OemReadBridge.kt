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
      "bodywork" -> "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
      else -> error("Unsupported device")
    }
    val cls = Class.forName(name, false, loader)
    cls.getMethod("getInstance", Context::class.java).invoke(null, context.applicationContext)
      ?: error("Device is not ready")
  }
  private fun number(kind: String, name: String, area: Int? = null): Double = GetterReadiness.read {
    StorageAccess.requireUnlocked(context)
    require(name in setOf("getSeatHorization", "getSeatbackrestPostion", "getSeatTurnHeight", "getDoorLockStatus", "getEPBState") || (kind=="bodywork" && name in absoluteGetters))
    val obj = device(kind)
    val method = if (area == null) obj.javaClass.getMethod(name) else obj.javaClass.getMethod(name, Int::class.javaPrimitiveType)
    val expected = if (name in setOf("getSeatbackrestPostion", "getSeatTurnHeight") || name in absoluteGetters) Float::class.javaPrimitiveType else Int::class.javaPrimitiveType
    check(method.returnType == expected) { "Getter signature changed" }
    val value = if (area == null) method.invoke(obj) else method.invoke(obj, area)
    (value as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: error("Getter is unavailable")
  }
  fun horizontalProperty(): Pair<Double, CoordinateEvidence>? = runCatching {
    val key=PresetChannel.key(context,firstUse=false) ?: return@runCatching null
    dadb.Dadb.create("127.0.0.1",5555,key,connectTimeout=400,socketTimeout=1500).use { channel ->
      val result=channel.shell(SeatPropertyRead.COMMAND)
      if(result.exitCode!=0) return@use null
      SeatPropertyRead.parse(result.output)?.let {
        it.percent to CoordinateEvidence("car-service.0x21603b80",android.os.SystemClock.elapsedRealtime(),it.cacheAtNs)
      }
    }
  }.onFailure { SeatDiagnostics.event(context,"property-read","unavailable="+SeatDiagnostics.failure(it)) }.getOrNull()
  fun seat(): Position {
    val values=linkedMapOf<String,Double>()
    val evidence=linkedMapOf<String,CoordinateEvidence>()
    mapOf("horizontal" to "getSeatHorization","backrest" to "getSeatbackrestPostion","height" to "getSeatTurnHeight").forEach { (axis,method) ->
      values[axis]=number("setting",method,1)
      evidence[axis]=CoordinateEvidence("setting.$method",android.os.SystemClock.elapsedRealtime())
    }
    if(values["horizontal"]==0.0) horizontalProperty()?.let { (raw, provenance) -> values["horizontal"]=raw;evidence["horizontal"]=provenance }
    check(SeatObservation.usable(values)) { "Seat readings are unavailable or outside the candidate range" }
    Startup.times.sampled(android.os.SystemClock.elapsedRealtime())
    return Position(values, "byd-setting-driver-1:" + java.security.MessageDigest.getInstance("SHA-256").digest((android.os.Build.FINGERPRINT + ":" + context.packageManager.getPackageInfo("com.byd.data.collect", 0).longVersionCode).toByteArray()).joinToString("") { "%02x".format(it) }.take(32), SeatObservation.FORMAT,evidence)
      .also { SeatDiagnostics.event(context,"seat-read",SeatDiagnostics.position("observed",it)) }
  }
  fun backrest(): Double = number("setting","getSeatbackrestPostion",1)
  /** Exact setting getters as raw diagnostic values, without fallback or preset conversion. */
  fun percentageSeatCandidates(): Map<String, Double?> = mapOf(
    "horizontal" to "getSeatHorization", "backrest" to "getSeatbackrestPostion", "height" to "getSeatTurnHeight"
  ).mapValues { (_, method) ->
    runCatching { number("setting", method, 1) }
      .onFailure { SeatDiagnostics.event(context,"native-trial-read","getter=$method cause="+SeatDiagnostics.failure(it)) }.getOrNull()
  }
  /** Separate raw geometry candidates. Never saved as percentage presets or passed to a writer. */
  fun absoluteSeatCandidates(): Map<String, Double?> {
    listOf("BODYWORK_COMMON","BODYWORK_GET").forEach { suffix ->
      check(context.checkSelfPermission("android.permission.BYDAUTO_$suffix")==android.content.pm.PackageManager.PERMISSION_GRANTED) { "Missing $suffix read permission" }
    }
    return absoluteGetters.associateWith { method ->
      runCatching { number("bodywork",method) }
        .onFailure { SeatDiagnostics.event(context,"absolute-read","getter=$method cause="+SeatDiagnostics.failure(it)) }.getOrNull()
    }
  }
  private val absoluteGetters=setOf("getDriverSeatHorizontalPosition","getDriverSeatBackrestPosition","getDriverSeatHeightPosition",
    "getDriverSeatSitpointPosition","getDriverSeatLegholderPosition","getDriverSeatHeadrestHorizontalPosition","getDriverSeatHeadrestVerticalPosition")
  /** Metadata only. No candidate setter, constructor or SDK factory is invoked by this inventory. */
  fun seatSignatures(): String = listOf("setting", "bodywork").joinToString("\n") { kind ->
    val className = "android.hardware.bydauto.$kind.BYDAuto${if (kind == "setting") "Setting" else "Bodywork"}Device"
    runCatching {
      val cls = Class.forName(className, false, loader)
      val methods = cls.methods.filter { it.name.contains("seat", ignoreCase = true) || it.name in setOf("get", "set") }
        .map { "${it.declaringClass.name}.${it.name}(${it.parameterTypes.joinToString { type -> type.typeName }}):${it.returnType.typeName}" }.distinct().sorted()
      "$className signatures=${methods.size}; metadata only, invocation/units/physical completion unverified\n" +
        methods.take(100).joinToString("\n") + if (methods.size > 100) "\n[SIGNATURES CAPPED]" else ""
    }.getOrElse { "$className inventory unavailable: ${SeatDiagnostics.failure(it)}" }
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
    val brake = runCatching { number("gearbox", "getEPBState").toInt() }
      .onFailure { SeatDiagnostics.event(context,"park-read","EPB unavailable cause="+SeatDiagnostics.failure(it)) }.getOrNull()
    return gear to brake
  }
  fun parkedVetoClear(): Boolean {
    val raw = rawPark()
    // This checks reported state only, not cache freshness. Physical confirmation is still required.
    return GearModeRead.parked(raw.first, raw.second)
  }
  /** User-requested diagnostic exception. Exactly one backrest command, never a full recall. */
  fun sendBackrestTest(permit:BackrestTestPermit,foreground:()->Boolean,markAttempt:()->Unit) {
    StorageAccess.requireUnlocked(context)
    val obj=device("setting")
    val method=obj.javaClass.getMethod("turnSeatbackrestPercent",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
    check(method.returnType==Void.TYPE) { "Backrest setter signature changed" }
    check(context.checkSelfPermission("android.permission.BYDAUTO_SETTING_SET")==android.content.pm.PackageManager.PERMISSION_GRANTED) { "Missing seat-write permission" }
    val observed=backrest()
    val park=rawPark()
    val awake=(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
    val state=VehicleSnapshot(if(park.first==1) Gear.P else null,if(park.second==3) true else null,null,android.os.SystemClock.elapsedRealtime())
    // No disk I/O or another queued action between the final guard and this single invocation.
    val target=permit.consume(android.os.SystemClock.elapsedRealtime(),observed,state,foreground() && awake,SeatControl.enabled(context))
    markAttempt() // Memory flag only. The caller must not do I/O here.
    method.invoke(obj,1,target)
  }
  fun bindAxisWriter(axis: String): (Int) -> Unit {
    check(SeatWritePolicy.allowed()) { SeatWritePolicy.BLOCK_REASON }
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
      // Defense in depth, including callers that bypass the shared recall controller.
      check(SeatWritePolicy.allowed()) { SeatWritePolicy.BLOCK_REASON }
      method.invoke(obj, 1, value)
    }
  }

}

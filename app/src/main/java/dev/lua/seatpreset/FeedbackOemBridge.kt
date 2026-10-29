package dev.lua.seatpreset

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import dadb.Dadb
import dalvik.system.PathClassLoader

/** Fixed typed reads and three native momentary setters. No native-bank or percentage command. */
internal class FeedbackOemBridge(private val context:Context,private val trace:String) : AutoCloseable {
  private var channel:Dadb?=null
  private val loader by lazy {
    StorageAccess.requireUnlocked(context)
    val info=context.packageManager.getApplicationInfo("com.byd.data.collect",0)
    check(info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM!=0) { "OEM SDK package is not a system app" }
    PathClassLoader((listOf(info.sourceDir)+info.splitSourceDirs.orEmpty()).joinToString(":"),context.classLoader)
  }
  private val device by lazy {
    val cls=Class.forName("android.hardware.bydauto.setting.BYDAutoSettingDevice",false,loader)
    checkNotNull(cls.getMethod("getInstance",Context::class.java).invoke(null,context.applicationContext))
  }
  private val setters by lazy {
    check(context.checkSelfPermission("android.permission.BYDAUTO_SETTING_SET")==PackageManager.PERMISSION_GRANTED) { "Missing native seat-control access" }
    mapOf("horizontal" to "turnSeatHorization","backrest" to "turnSeatbackrest","height" to "turnSeatHeight").mapValues { (_,name)->
      device.javaClass.getMethod(name,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).also { check(it.returnType==Void.TYPE) { "Native signal signature changed" } }
    }
  }
  fun prepareSignals() {
    val bound=setters
    SeatDiagnostics.event(context,trace,"nativeBindings receiver=${device.javaClass.name} driverArea=1 methods="+bound.mapValues { it.value.name }+" absoluteWrites=false")
  }
  fun read():Map<String,PositionFeedback.Sample> {
    StorageAccess.requireUnlocked(context)
    val connection=channel ?: run {
      val key=PresetChannel.key(context,firstUse=false) ?: error("Set up vehicle readings first")
      Dadb.create("127.0.0.1",5555,key,connectTimeout=500,socketTimeout=2000).also { channel=it }
    }
    val result=connection.shell(PositionFeedback.COMMAND)
    check(result.exitCode==0) { "Typed position feedback is unavailable" }
    return runCatching { PositionFeedback.parse(result.output) }.onFailure {
      SeatDiagnostics.event(context,trace,"typedReadRejected cause="+SeatDiagnostics.failure(it)+" raw="+result.output.take(3000))
    }.getOrThrow()
  }
  fun position():Position {
    val values=read();val sampledAt=SystemClock.elapsedRealtime()
    return Position(values.mapValues { it.value.value },source(context),PositionFeedback.FORMAT,
      values.mapValues { (axis,raw)->CoordinateEvidence(PositionFeedback.route(axis),sampledAt,raw.cacheAtNs) }).also {
      check(PositionFeedback.accepts(it));SeatDiagnostics.event(context,trace,SeatDiagnostics.position("typed-observation",it))
    }
  }
  fun signal(axis:String,direction:Int) {
    require(axis in PositionFeedback.seatAxes && direction in 0..2)
    // Stopping is allowed after opt-out, P loss or screen loss. Never put a veto before release.
    if(direction!=0) { StorageAccess.requireUnlocked(context);check(SeatControl.enabled(context)) { "Seat movement disabled" } }
    setters.getValue(axis).invoke(device,1,direction)
  }
  override fun close() { runCatching { channel?.close() };channel=null }
  companion object {
    fun source(context:Context):String {
      StorageAccess.requireUnlocked(context)
      val revision=context.packageManager.getPackageInfo("com.byd.data.collect",0).longVersionCode
      val hash=java.security.MessageDigest.getInstance("SHA-256").digest((android.os.Build.FINGERPRINT+":"+revision).toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
      return "byd-float-driver-1:$hash"
    }
  }
}

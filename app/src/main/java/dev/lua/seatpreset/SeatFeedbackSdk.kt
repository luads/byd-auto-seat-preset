package dev.lua.seatpreset

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader

/** Fixed getter allowlist. No setter lookup, generic dispatch, permission grant or preset conversion. */
internal class SeatFeedbackSdk(private val context:Context,private val axis:String,private val trace:String) {
  private val bound=mutableMapOf<String,()->Double>()
  private val loaders=mutableMapOf<SeatSdkSource,ClassLoader>()
  private val failures=mutableMapOf<String,String>()
  init { require(axis in PositionFeedback.seatAxes) }
  fun read(route:String):Double {
    require(route in FeedbackProbe.routes && route.contains('.'))
    failures[route]?.let { error(it) }
    val getter=bound[route] ?: try { bind(route).also { bound[route]=it } } catch(e:Exception) {
      if(e is ClassNotFoundException || e is NoSuchMethodException)failures[route]=SeatDiagnostics.failure(e);throw e
    }
    return getter()
  }
  private fun bind(route:String):()->Double {
    StorageAccess.requireUnlocked(context)
    val source=SeatSdkSource.valueOf(route.substringBefore('.'))
    val kind=route.substringAfter('.')
    val methodName=when(kind) {
      "setting" -> mapOf("horizontal" to "getSeatHorization","backrest" to "getSeatbackrestPostion","height" to "getSeatTurnHeight").getValue(axis)
      "bodywork" -> mapOf("horizontal" to "getDriverSeatHorizontalPosition","backrest" to "getDriverSeatBackrestPosition","height" to "getDriverSeatHeightPosition").getValue(axis)
      else -> error("Unsupported getter group")
    }
    val loader=loaders.getOrPut(source) {
      val info=context.packageManager.getApplicationInfo(source.packageName,PackageManager.GET_SHARED_LIBRARY_FILES)
      check(info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM!=0) { "SDK package is not a system app" }
      PathClassLoader((listOf(info.sourceDir)+info.splitSourceDirs.orEmpty()+info.sharedLibraryFiles.orEmpty()).distinct().joinToString(":"),context.classLoader)
    }
    val cls=Class.forName("android.hardware.bydauto.$kind.BYDAuto${if(kind=="setting") "Setting" else "Bodywork"}Device",false,loader)
    val device=checkNotNull(cls.getMethod("getInstance",Context::class.java).invoke(null,context.applicationContext))
    val method=if(kind=="setting") cls.getMethod(methodName,Int::class.javaPrimitiveType) else cls.getMethod(methodName)
    check(method.returnType in listOf(Int::class.javaPrimitiveType,Float::class.javaPrimitiveType,Double::class.javaPrimitiveType)) { "Getter return type is not numeric" }
    SeatDiagnostics.event(context,trace,"feedback-binding route=$route axis=$axis getter=$methodName returnType=${method.returnType.name} area=${if(kind=="setting") 1 else "none"} readOnly=true")
    return {
      StorageAccess.requireUnlocked(context)
      val raw=if(kind=="setting") method.invoke(device,1) else method.invoke(device)
      (raw as? Number)?.toDouble() ?: error("Getter returned no number")
    }
  }
}

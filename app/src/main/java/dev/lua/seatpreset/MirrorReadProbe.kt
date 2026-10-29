package dev.lua.seatpreset

import android.content.Context
import dalvik.system.PathClassLoader

/** Bounded fixed getter comparison. Results are diagnostics, never movement targets. */
internal object MirrorReadProbe {
  data class Route(val axis:String,val area:Int,val method:String,val fid:Int,val otherSdkFid:Int)
  val routes=listOf(Route("mirror.left.x",1,"getRearviewMirrorX",2120,0x4B300188),Route("mirror.left.y",1,"getRearviewMirrorY",2122,0x4B300190),
    Route("mirror.right.x",2,"getRearviewMirrorX",2130,0x4BB00110),Route("mirror.right.y",2,"getRearviewMirrorY",2132,0x4BB00118))
  fun read(context:Context,trace:String):String {
    StorageAccess.requireUnlocked(context)
    val output=mutableListOf("Read-only comparison. SDK defaults are not saved coordinates. Mirror movement is not enabled.")
    fun add(line:String) { output+=line;SeatDiagnostics.event(context,trace,line) }
    fun typed(stage:String) {
      runCatching { FeedbackOemBridge(context,trace).use { it.read() } }.fold({ frame ->
        frame.filterKeys { it.startsWith("mirror.") }.forEach { (axis,value)->add("mirror-typed stage=$stage axis=$axis raw=${value.value} cacheAtNs=${value.cacheAtNs} sampledAtMs=${android.os.SystemClock.elapsedRealtime()} freshness=unverified") }
      },{ add("mirror-typed stage=$stage unavailable="+SeatDiagnostics.failure(it)) })
    }
    typed("before")
    for(source in SeatSdkSource.values()) runCatching {
      val info=context.packageManager.getApplicationInfo(source.packageName,android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES)
      check(info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM!=0) { "SDK source is not a system app" }
      val loader=PathClassLoader((listOf(info.sourceDir)+info.splitSourceDirs.orEmpty()+info.sharedLibraryFiles.orEmpty()).distinct().joinToString(":"),context.classLoader)
      val cls=Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",false,loader)
      val device=checkNotNull(cls.getMethod("getInstance",Context::class.java).invoke(null,context.applicationContext))
      for(route in routes) {
        val direct=runCatching { GetterReadiness.read { cls.getMethod(route.method,Int::class.javaPrimitiveType).invoke(device,route.area) } }
          .fold({ it.toString() },{ "unavailable:"+SeatDiagnostics.failure(it) })
        add("mirror-read sdk=$source axis=${route.axis} route=${route.method} area=${route.area} raw=$direct sampledAtMs=${android.os.SystemClock.elapsedRealtime()} freshness=unverified")
        for(fid in listOf(route.fid,route.otherSdkFid)) {
          val raw=runCatching {
            val event=GetterReadiness.read { cls.getMethod("get",IntArray::class.java,Class::class.java).invoke(device,intArrayOf(fid),Float::class.javaPrimitiveType) }
            checkNotNull(event);listOf("floatValue","doubleValue","intValue").joinToString { field->field+"="+runCatching { event.javaClass.getField(field).get(event) }.getOrNull() }
          }.getOrElse { "unavailable:"+SeatDiagnostics.failure(it) }
          add("mirror-read sdk=$source axis=${route.axis} fid=0x${fid.toString(16)} raw=$raw sampledAtMs=${android.os.SystemClock.elapsedRealtime()} freshness=unverified")
        }
      }
    }.onFailure { add("mirror-sdk sdk=$source unavailable="+SeatDiagnostics.failure(it)) }
    typed("after")
    return output.joinToString("\n\n")
  }
}

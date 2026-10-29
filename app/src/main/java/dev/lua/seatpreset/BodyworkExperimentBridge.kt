package dev.lua.seatpreset

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import dalvik.system.PathClassLoader

/** Owned, isolated SDK loader. No shared-classloader injection or permission/context replacement. */
internal class BodyworkExperimentBridge(private val context:Context,val source:SeatSdkSource) {
  private val loader by lazy {
    StorageAccess.requireUnlocked(context)
    val info=context.packageManager.getApplicationInfo(source.packageName,PackageManager.GET_SHARED_LIBRARY_FILES)
    check(info.flags and ApplicationInfo.FLAG_SYSTEM!=0) { "The selected SDK is not an installed system app" }
    val paths=(listOf(info.sourceDir)+info.splitSourceDirs.orEmpty()+info.sharedLibraryFiles.orEmpty()).distinct()
    PathClassLoader(paths.joinToString(":"),context.classLoader)
  }
  private val eventClass by lazy { Class.forName("android.hardware.bydauto.BYDAutoEventValue",false,loader) }
  private val device by lazy {
    val cls=Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",false,loader)
    checkNotNull(cls.getMethod("getInstance",Context::class.java).invoke(null,context.applicationContext)) { "Bodywork SDK not ready" }
  }
  private fun requireAccess(write:Boolean=false) {
    StorageAccess.requireUnlocked(context)
    (listOf("BODYWORK_COMMON","BODYWORK_GET")+if(write) listOf("BODYWORK_SET") else emptyList()).forEach {
      check(context.checkSelfPermission("android.permission.BYDAUTO_$it")==PackageManager.PERMISSION_GRANTED) { "Missing $it. Use experiment access setup." }
    }
  }
  fun read(trace:String,requested:Set<String> = SeatExperiment.axes.toSet(),detailed:Boolean=true):Map<String,Double?> {
    requireAccess()
    val obj=device
    val get=obj.javaClass.getMethod("get",IntArray::class.java,Class::class.java)
    check(get.returnType==eventClass) { "Unexpected generic getter shape" }
    require(requested.isNotEmpty() && requested.all { it in SeatExperiment.axes })
    return SeatExperiment.axes.filter { it in requested }.map { axis ->
      val index=SeatExperiment.axes.indexOf(axis)
      val candidates=mutableListOf<Double>()
      val attempts=mutableListOf<String>()
      for(shape in listOf(Double::class.javaPrimitiveType,Float::class.javaPrimitiveType,eventClass)) {
        runCatching {
          val event=GetterReadiness.read { get.invoke(obj,intArrayOf(SeatExperiment.readIds[index]),shape) }
          val raw=mutableListOf<Double>()
          if(event is Number) raw+=event.toDouble()
          else if(event!=null) {
            for(field in listOf("doubleValue","floatValue")) runCatching { (event.javaClass.getField(field).get(event) as? Number)?.toDouble() }.getOrNull()?.let { raw+=it }
            for(field in listOf("doubleArrayValue","floatArrayValue")) runCatching { event.javaClass.getField(field).get(event) }.getOrNull()?.let { array ->
              if(java.lang.reflect.Array.getLength(array)==1) (java.lang.reflect.Array.get(array,0) as? Number)?.toDouble()?.let { raw+=it }
            }
          }
          attempts+="${shape?.simpleName}:$raw"
          candidates+=raw.filter { SeatExperiment.usable(it) }
        }.onFailure { attempts+="${shape?.simpleName}:unavailable:${SeatDiagnostics.failure(it)}" }
      }
      val distinct=candidates.distinct()
      val value=distinct.singleOrNull()
      if(detailed || value==null) SeatDiagnostics.event(context,trace,"genericRead sdk=$source axis=$axis readId=0x${SeatExperiment.readIds[index].toString(16)} raw=$attempts selected=$value sampledAtMs=${SystemClock.elapsedRealtime()} receiver=${obj.javaClass.name} freshness=unverified")
      axis to value
    }.toMap()
  }
  fun requireWritableBackend() {
    requireAccess(write=true)
    check(source==SeatSdkSource.NATIVE) { "Collector SDK float writes are quarantined" }
    // The reviewed compatibility manager converts generic float values to integer zero.
    // Inspect manager class ancestry only; never replace context, permissions, or fields.
    val ancestry=mutableListOf<String>()
    var owner:Class<*>?=device.javaClass
    while(owner!=null && owner!=Any::class.java) {
      for(field in owner.declaredFields.filter { it.name.contains("manager",true) && !java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
        val manager=runCatching { field.isAccessible=true;field.get(device)?.javaClass }.getOrElse { error("Cannot inspect SDK dispatcher. Writes are unavailable.") }
        checkNotNull(manager) { "SDK dispatcher is not ready" }.let { managerClass ->
          var type:Class<*>?=managerClass
          while(type!=null && type!=Any::class.java) {
            ancestry+=type.name
            type=type.superclass
          }
        }
      }
      owner=owner.superclass
    }
    SeatExperiment.requireBackend(ancestry)
  }
  fun send(trace:String,permit:SeatExperimentPermit,park:()->VehicleSnapshot,foreground:()->Boolean,markAttempt:()->Unit):Int {
    requireWritableBackend()
    check(permit.source==source)
    val obj=device;val cls=eventClass
    val method=obj.javaClass.getMethod("set",IntArray::class.java,cls)
    check(method.returnType==Int::class.javaPrimitiveType) { "Unexpected generic setter shape" }
    val event=cls.getConstructor().newInstance()
    val field=cls.getField("floatArrayValue");check(field.type==FloatArray::class.java) { "SDK has no float-array payload" }
    val ids=SeatExperiment.ids(permit.values)
    field.set(event,permit.values.values.toFloatArray())
    val current=read(trace,permit.values.keys)
    val state=park()
    permit.consume(SystemClock.elapsedRealtime(),current,state,foreground(),SeatControl.enabled(context),source)
    // Final guard is followed only by a memory flag and the one SDK invocation. No retries.
    markAttempt()
    return (method.invoke(obj,ids,event) as Number).toInt()
  }
}

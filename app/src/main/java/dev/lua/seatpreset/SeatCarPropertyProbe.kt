package dev.lua.seatpreset

import android.content.Context
import dalvik.system.PathClassLoader

/** Optional public Car API, fixed read-only properties. No listener, setter or permission changes. */
internal class SeatCarPropertyProbe(private val context:Context,private val axis:String):AutoCloseable {
  private var car:Any?=null
  private var carClass:Class<*>?=null
  private val manager by lazy {
    StorageAccess.requireUnlocked(context)
    val cls=try { Class.forName("android.car.Car",false,context.classLoader) } catch(e:ClassNotFoundException) {
      val path="/system/framework/android.car.jar"
      check(java.io.File(path).isFile) { "Public Car API library unavailable" }
      Class.forName("android.car.Car",false,PathClassLoader(path,context.classLoader))
    }
    carClass=cls
    val instance=checkNotNull(cls.getMethod("createCar",Context::class.java).invoke(null,context.applicationContext)) { "Car service unavailable" }
    car=instance
    checkNotNull(cls.getMethod("getCarManager",String::class.java).invoke(instance,"property")) { "Property manager unavailable" }
  }
  fun read():PositionFeedback.Sample {
    StorageAccess.requireUnlocked(context)
    require(axis in PositionFeedback.seatAxes)
    val property=PositionFeedback.properties.getValue(axis).removePrefix("0x").toInt(16)
    val obj=manager
    val event=checkNotNull(obj.javaClass.getMethod("getProperty",Class::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
      .invoke(obj,java.lang.Float::class.java,property,0)) { "Property returned no event" }
    fun field(name:String)=event.javaClass.getMethod(name).invoke(event)
    return TypedSeatProbe.value(axis,field("getPropertyId") as Int,field("getAreaId") as Int,field("getStatus") as Int,field("getValue"),field("getTimestamp") as Long)
  }
  override fun close() { val obj=car;car=null;if(obj!=null)runCatching { carClass?.getMethod("disconnect")?.invoke(obj) } }
}

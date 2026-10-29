package dev.lua.seatpreset

/** Fixed READ-only VHAL cache routes verified against the owner car's declared access bits. */
internal object PositionFeedback {
  const val FORMAT="byd-float-driver-feedback-v2"
  const val COMMAND="dumpsys -t 1 car_service | grep -E 'lastEvent:Property:0x21603b(80|84|88|7c|7d|7e|7f),'"
  val seatAxes=listOf("horizontal","backrest","height")
  val properties=linkedMapOf("horizontal" to "0x21603b80","backrest" to "0x21603b88","height" to "0x21603b84",
    "mirror.left.x" to "0x21603b7e","mirror.left.y" to "0x21603b7c","mirror.right.x" to "0x21603b7f","mirror.right.y" to "0x21603b7d")
  data class Sample(val value:Double,val cacheAtNs:Long)
  fun parse(raw:String):Map<String,Sample> {
    require(raw.length<=16_384) { "Position feedback exceeded its bound" }
    val result=linkedMapOf<String,Sample>()
    val seen=mutableSetOf<String>()
    val pattern=Regex("lastEvent:Property:(0x[0-9a-f]+),status: *(-?[0-9]+),timestamp:([0-9]+),zone:(0x[0-9a-f]+),floatValues: *\\[([^]]*)],int32Values: *\\[([^]]*)],int64Values: *\\[([^]]*)],bytes: *\\[([^]]*)],string: *$",RegexOption.IGNORE_CASE)
    raw.lineSequence().forEach { line ->
      val id=Regex("lastEvent:Property:(0x[0-9a-f]+),",RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.lowercase() ?: return@forEach
      val axis=properties.entries.firstOrNull { it.value==id }?.key ?: return@forEach
      require(seen.add(axis)) { "Ambiguous $axis feedback" }
      val m=pattern.find(line) ?: return@forEach
      val value=m.groupValues[5].trim().toDoubleOrNull()
      val time=m.groupValues[3].toLongOrNull()
      if(m.groupValues[2]=="0" && m.groupValues[4].equals("0x0",true) && m.groupValues[6].isBlank() && m.groupValues[7].isBlank() && m.groupValues[8].isBlank() &&
        value!=null && value.isFinite() && value in 0.0..100.0 && time!=null && time>0) result[axis]=Sample(value,time)
    }
    require(seatAxes.all { it in result }) { "Typed seat feedback is unavailable. No default coordinates were used." }
    return result
  }
  fun route(axis:String)="car-service.float.${properties.getValue(axis)}.zone0"
  fun accepts(position:Position):Boolean = position.coordinateFormat==FORMAT && seatAxes.all { it in position.coordinates } &&
    position.coordinates.keys.all { it in properties } && position.coordinates.values.all { it.isFinite() && it in 0.0..100.0 } &&
    position.coordinates.keys.all { axis -> position.evidence[axis]?.let { it.route==route(axis) && it.cacheAtNs!=null && it.cacheAtNs>0 && it.sampledAtMs>=0 }==true }
}

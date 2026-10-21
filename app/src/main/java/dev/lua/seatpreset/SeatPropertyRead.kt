package dev.lua.seatpreset

/** Fixed readable property, checked on the owner car. Cache timestamps are never called fresh. */
internal object SeatPropertyRead {
  const val COMMAND="dumpsys -t 1 car_service | grep -m 1 -F 'lastEvent:Property:0x21603b80,'"
  data class Reading(val percent: Double,val cacheAtNs: Long)
  fun parse(raw: String): Reading? {
    if(raw.length>4096) return null
    val match=Regex("lastEvent:Property:0x21603b80,status: *0,timestamp:([0-9]+),zone:0x0,floatValues: *\\[([0-9.eE+\\-]+)\\],int32Values: *\\[\\]",RegexOption.IGNORE_CASE).find(raw) ?: return null
    val time=match.groupValues[1].toLongOrNull()?.takeIf { it>0 } ?: return null
    val value=match.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
    return Reading(value,time)
  }
}

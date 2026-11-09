package dev.lua.seatpreset

/** Same fixed Float/status/area contract as the ADB cache parser, still diagnostic-only. */
internal object TypedSeatProbe {
  fun value(axis:String,property:Int,area:Int,status:Int,raw:Any?,cacheAtNs:Long):PositionFeedback.Sample {
    require(axis in PositionFeedback.seatAxes)
    check(property==PositionFeedback.properties.getValue(axis).removePrefix("0x").toInt(16) && area==0 && status==0) { "Unexpected property, area or status" }
    check(raw is Float && raw.isFinite() && raw in 0f..100f && cacheAtNs>0) { "Typed Float feedback unavailable" }
    return PositionFeedback.Sample(raw.toDouble(),cacheAtNs)
  }
}

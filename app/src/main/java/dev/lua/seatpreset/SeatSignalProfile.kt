package dev.lua.seatpreset

import android.content.Context

/** Owner-confirmed numeric polarity, scoped to the current firmware/SDK source. No auto learning. */
internal object SeatSignalProfile {
  fun infer(axis:String,direction:Int,before:Map<String,PositionFeedback.Sample>,after:Map<String,PositionFeedback.Sample>):Int {
    require(axis in PositionFeedback.seatAxes && direction in 1..2)
    val a=checkNotNull(before[axis]);val b=checkNotNull(after[axis])
    require(a.value.isFinite() && b.value.isFinite() && a.value in 0.0..100.0 && b.value in 0.0..100.0 && b.cacheAtNs>a.cacheAtNs)
    val delta=b.value-a.value
    require(kotlin.math.abs(delta)>0 && kotlin.math.abs(delta)<=10) { "No usable single-pulse change" }
    require(PositionFeedback.seatAxes.filter { it!=axis }.all { before[it]!=null && after[it]?.value==before[it]?.value }) { "Another seat axis changed" }
    return if(delta>0) direction else 3-direction
  }
  fun read(context:Context,source:String):Map<String,Int> {
    val prefs=StorageAccess.prefs(context,"native-signal-profile")
    return PositionFeedback.seatAxes.associateWith { axis ->
      prefs.getInt("$source:$axis",1).also { check(it in 1..2) { "Invalid $axis direction profile" } }
    }
  }
  fun save(context:Context,source:String,axis:String,increasingSignal:Int) {
    require(axis in PositionFeedback.seatAxes && increasingSignal in 1..2)
    check(StorageAccess.prefs(context,"native-signal-profile").edit().putInt("$source:$axis",increasingSignal).commit()) { "Direction preference could not be saved" }
  }
}

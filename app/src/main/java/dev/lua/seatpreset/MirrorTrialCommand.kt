package dev.lua.seatpreset

/** SDK-declared tilt actions only. Not native-bank, fold or absolute-coordinate control. */
internal object MirrorTrialCommand {
  val axes=listOf("mirror.left.x","mirror.left.y","mirror.right.x","mirror.right.y")
  val labels=listOf("Left mirror · horizontal","Left mirror · vertical","Right mirror · horizontal","Right mirror · vertical")
  val constants=linkedMapOf("ACTION_LEFT_LEFT" to 1,"ACTION_LEFT_RIGHT" to 2,"ACTION_LEFT_UP" to 3,"ACTION_LEFT_DOWN" to 4,
    "ACTION_RIGHT_LEFT" to 5,"ACTION_RIGHT_RIGHT" to 6,"ACTION_RIGHT_UP" to 7,"ACTION_RIGHT_DOWN" to 8)
  fun action(axis:String,direction:Int):Int {
    require(axis in axes && direction in 0..2)
    return if(direction==0) 0 else axes.indexOf(axis)*2+direction
  }
  fun requireFeedback(axis:String,frame:Map<String,PositionFeedback.Sample>) {
    require(axis in axes)
    check((axes+PositionFeedback.seatAxes).all { name -> frame[name]?.let { it.value.isFinite() && it.value in 0.0..100.0 && it.cacheAtNs>0 }==true }) { "Read all seat and mirror coordinates before a mirror trial" }
  }
  fun changedOnlySelected(axis:String,before:Map<String,PositionFeedback.Sample>,after:Map<String,PositionFeedback.Sample>):Boolean {
    requireFeedback(axis,before);requireFeedback(axis,after)
    return after.getValue(axis).cacheAtNs>before.getValue(axis).cacheAtNs && after.getValue(axis).value!=before.getValue(axis).value &&
      (axes+PositionFeedback.seatAxes).filter { it!=axis }.all { before.getValue(it).value==after.getValue(it).value }
  }
}

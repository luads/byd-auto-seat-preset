package dev.lua.seatpreset

import kotlin.math.abs

/** Bounded independent-coordinate loading through native momentary signals, never percentage writes. */
internal object FeedbackSeatRecall {
  const val PULSE_MS=100L
  const val MAX_RUN_MS=30_000L
  const val MAX_PULSES=48
  const val MAX_AXIS_PULSES=24
  const val FEEDBACK_WAIT_MS=1_500L
  const val TOLERANCE=.49
  interface Port {
    fun clock():Long
    fun pause(ms:Long)
    fun enabled():Boolean
    fun active():Boolean
    fun vehicle():VehicleSnapshot
    fun read():Map<String,PositionFeedback.Sample>
    fun increasingSignal(axis:String):Int=1
    fun signal(axis:String,direction:Int)
    fun audit(detail:String)
  }
  data class Outcome(val pulses:Int,val axes:Set<String>)
  fun run(port:Port,target:Map<String,Double>,requestedAt:Long):Outcome {
    require(target.isNotEmpty() && target.keys.all { it in PositionFeedback.seatAxes } && target.values.all { it.isFinite() && it in 0.0..100.0 })
    val increasing=target.keys.associateWith { port.increasingSignal(it).also { signal -> require(signal in 1..2) { "Invalid native direction profile" } } }
    var pulses=0
    val moved=linkedSetOf<String>()
    var acceptedAt:Long?=null
    fun guard() {
      check(port.enabled() && port.active()) { "Seat load cancelled, movement disabled or control surface closed" }
      val state=port.vehicle();val now=port.clock()
      RecallPolicy.blockReason(state,now)?.let { error(it) }
      val start=acceptedAt
      if(start==null) check(requestedAt>=0 && now>=requestedAt && now-requestedAt<=RecallPolicy.MAX_AGE_MS) { "Seat request expired before its first signal" }
      else check(now>=start && now-start<=MAX_RUN_MS) { "Seat load time limit reached" }
    }
    guard()
    var current=port.read()
    guard()
    for(axis in PositionFeedback.seatAxes.filter { it in target }) {
      var count=0
      while(true) {
        guard()
        val frameBefore=current.toMap()
        val before=checkNotNull(current[axis]) { "Missing $axis feedback" }
        val wanted=target.getValue(axis)
        val error=wanted-before.value
        if(abs(error)<=TOLERANCE) break
        check(pulses<MAX_PULSES && count<MAX_AXIS_PULSES) { "Seat signal limit reached before matching $axis" }
        val direction=if(error>0) increasing.getValue(axis) else 3-increasing.getValue(axis)
        port.audit("prepare axis=$axis from=${before.value} target=$wanted signal=$direction release=0 pulseMs=$PULSE_MS cacheAtNs=${before.cacheAtNs}")
        guard()
        if(acceptedAt==null) acceptedAt=port.clock()
        pulses++;count++;moved+=axis
        // A release is attempted even when start throws. No retry, fallback, or opposite correction.
        var releaseFailure:Throwable?=null
        try {
          port.signal(axis,direction)
          port.pause(PULSE_MS)
        } finally {
          try { port.signal(axis,0) } catch(error:Throwable) { releaseFailure=error }
          port.audit("release axis=$axis requested=true returned=${releaseFailure==null} physicalStop=unverified")
          releaseFailure?.let { throw it }
        }
        guard()
        port.pause(100) // Brief settling interval; no signal remains intentionally held here.
        val releasedAt=port.clock()
        var after:PositionFeedback.Sample?=null
        while(port.clock()-releasedAt<=FEEDBACK_WAIT_MS) {
          guard()
          current=port.read()
          guard()
          check(port.clock()-releasedAt<=FEEDBACK_WAIT_MS) { "Seat feedback arrived after its deadline. Load stopped." }
          val reading=current[axis]
          if(reading!=null && reading.cacheAtNs>before.cacheAtNs && reading.value!=before.value) { after=reading;break }
          port.pause(100)
        }
        val updated=checkNotNull(after) { "No advancing $axis feedback after release. Load stopped." }
        val next=wanted-updated.value
        port.audit("feedback axis=$axis before=${before.value} after=${updated.value} target=$wanted cacheAtNs=${updated.cacheAtNs} completion=unverified")
        check(abs(next)<abs(error)) { "$axis did not move toward its target. Load stopped. Check its direction in Seat trials." }
        check(abs(next)<=TOLERANCE || error*next>0) { "$axis passed its target. No reverse correction was sent." }
        check(PositionFeedback.seatAxes.filter { it!=axis }.all { other -> current[other]?.value==frameBefore[other]?.value }) { "Another seat axis changed. Load stopped." }
      }
    }
    guard()
    current=port.read()
    guard()
    check(target.all { (axis,value)->current[axis]?.let { abs(it.value-value)<=TOLERANCE }==true }) { "Final seat feedback does not match the preset" }
    return Outcome(pulses,moved)
  }
}

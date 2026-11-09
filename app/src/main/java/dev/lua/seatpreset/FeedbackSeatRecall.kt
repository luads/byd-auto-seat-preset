package dev.lua.seatpreset

import kotlin.math.abs

/** Bounded independent-coordinate loading through native momentary signals, never percentage writes. */
internal object FeedbackSeatRecall {
  const val PULSE_MS=100L
  const val MAX_PULSE_MS=300L
  const val GUARD_STEP_MS=25L
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
  fun run(port:Port,target:Map<String,Double>,requestedAt:Long,minimumPauseMs:Long=0L):Outcome {
    require(SeatCommandPause.valid(minimumPauseMs))
    require(target.isNotEmpty() && target.keys.all { it in PositionFeedback.seatAxes } && target.values.all { it.isFinite() && it in 0.0..100.0 })
    val increasing=target.keys.associateWith { port.increasingSignal(it).also { signal -> require(signal in 1..2) { "Invalid native direction profile" } } }
    var pulses=0
    val moved=linkedSetOf<String>()
    var acceptedAt:Long?=null
    var lastReleaseAt:Long?=null
    port.audit("timing minimumPauseMs=$minimumPauseMs basis=release-to-next-signal feedbackTimeCounts=true")
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
      var fastestRate=0.02 // Candidate units/ms, conservative floor above the observed average.
      var largestStep=2.0
      while(true) {
        guard()
        val previousRelease=lastReleaseAt
        if(previousRelease!=null && abs(target.getValue(axis)-checkNotNull(current[axis]).value)>TOLERANCE) {
          val waitStarted=port.clock()
          var waited=false
          while(port.clock()-previousRelease<minimumPauseMs) {
            guard()
            val remaining=minimumPauseMs-(port.clock()-previousRelease)
            if(remaining<=0) break
            waited=true
            port.pause(minOf(GUARD_STEP_MS,remaining))
          }
          guard()
          if(waited) {
            val refreshed=port.read()
            guard()
            check(PositionFeedback.seatAxes.all { refreshed[it]?.value==current[it]?.value }) { "Seat feedback changed during the command pause. Load stopped." }
            current=refreshed
          }
          port.audit("command-gap axis=$axis minimumPauseMs=$minimumPauseMs waitedMs=${port.clock()-waitStarted} sinceReleaseMs=${port.clock()-previousRelease}")
        }
        val frameBefore=current.toMap()
        val before=checkNotNull(current[axis]) { "Missing $axis feedback" }
        val wanted=target.getValue(axis)
        val error=wanted-before.value
        if(abs(error)<=TOLERANCE) break
        check(pulses<MAX_PULSES && count<MAX_AXIS_PULSES) { "Seat signal limit reached before matching $axis" }
        val duration=SeatPulseTiming.duration(abs(error),fastestRate,largestStep)
        val direction=if(error>0) increasing.getValue(axis) else 3-increasing.getValue(axis)
        port.audit("prepare axis=$axis from=${before.value} target=$wanted signal=$direction release=0 pulseMs=$duration mode=coarse-to-fine cacheAtNs=${before.cacheAtNs}")
        guard()
        if(acceptedAt==null) acceptedAt=port.clock()
        pulses++;count++;moved+=axis
        // A release is attempted even when start throws. No retry, fallback, or opposite correction.
        var releaseFailure:Throwable?=null
        val signalAt=port.clock()
        try {
          port.signal(axis,direction)
          val heldAt=port.clock()
          while(port.clock()-heldAt<duration) {
            val remaining=duration-(port.clock()-heldAt)
            if(remaining<=0) break
            port.pause(minOf(GUARD_STEP_MS,remaining))
            guard()
          }
        } finally {
          try { port.signal(axis,0) } catch(error:Throwable) { releaseFailure=error }
          port.audit("release axis=$axis requested=true returned=${releaseFailure==null} elapsedMs=${port.clock()-signalAt} plannedMs=$duration physicalStop=unverified")
          releaseFailure?.let { throw it }
        }
        guard()
        val releasedAt=port.clock()
        lastReleaseAt=releasedAt
        var after:PositionFeedback.Sample?=null
        var reads=0
        while(port.clock()-releasedAt<=FEEDBACK_WAIT_MS) {
          guard()
          val readAt=port.clock()
          current=port.read();reads++
          guard()
          val reading=current[axis]
          val changed=reading!=null && reading.cacheAtNs>before.cacheAtNs && reading.value!=before.value
          port.audit("readback axis=$axis attempt=$reads readMs=${port.clock()-readAt} sinceReleaseMs=${port.clock()-releasedAt} changed=$changed raw=${reading?.value} cacheAtNs=${reading?.cacheAtNs}")
          if(port.clock()-releasedAt>FEEDBACK_WAIT_MS) {
            check(!changed) { "Feedback arrived after its deadline. No further signal was sent." }
            break
          }
          if(changed) { after=reading;break }
          port.pause(100)
        }
        val updated=checkNotNull(after) { "No advancing $axis feedback after release. Load stopped." }
        val next=wanted-updated.value
        port.audit("feedback axis=$axis before=${before.value} after=${updated.value} target=$wanted cacheAtNs=${updated.cacheAtNs} completion=unverified")
        check(abs(next)<=TOLERANCE || error*next>0) { "$axis passed its target. No reverse correction was sent." }
        check(abs(next)<abs(error)) { "$axis did not move toward its target. Load stopped. Check its direction in Seat trials." }
        fastestRate=maxOf(fastestRate,abs(updated.value-before.value)/duration)
        largestStep=maxOf(largestStep,abs(updated.value-before.value))
        check(PositionFeedback.seatAxes.filter { it!=axis }.all { other -> current[other]?.value==frameBefore[other]?.value }) { "Another seat axis changed. Load stopped." }
      }
    }
    guard()
    current=port.read()
    guard()
    val mismatches=target.filter { (axis,value)->current[axis]?.let { abs(it.value-value)<=TOLERANCE }!=true }
    if(mismatches.isNotEmpty()) port.audit("final-feedback-drift mismatches=$mismatches observed=$current physicalStop=unverified reverseCommands=0")
    check(mismatches.isEmpty()) { "Seat feedback changed after matching a target. Load stopped without correction." }
    return Outcome(pulses,moved)
  }
}

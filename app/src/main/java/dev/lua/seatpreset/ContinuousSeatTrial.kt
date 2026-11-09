package dev.lua.seatpreset

import kotlin.math.abs

/** Simulation-first framework. No native adapter is wired until field feedback is verified. */
internal object ContinuousSeatTrial {
  const val MAX_HOLD_MS=1_000L
  const val MAX_SAMPLE_AGE_MS=150L
  data class Sample(val value:Double,val observedAt:Long)
  interface Port {
    fun clock():Long
    fun pause(ms:Long)
    fun eligible():Boolean
    /** Must be a non-blocking latest sample, never a synchronous ADB read while held. */
    fun latest():Sample
    fun signal(direction:Int)
  }
  fun simulate(port:Port,target:Double) {
    require(target.isFinite() && target in 0.0..100.0)
    fun sample():Sample {
      check(port.eligible()) { "Trial cancelled or eligibility lost" }
      val value=port.latest();val now=port.clock()
      check(value.value.isFinite() && value.value in 0.0..100.0 && value.observedAt<=now && now-value.observedAt<=MAX_SAMPLE_AGE_MS) { "Trial feedback unavailable or stale" }
      return value
    }
    val before=sample();val initialError=target-before.value
    if(abs(initialError)<=FeedbackSeatRecall.TOLERANCE)return
    val began=port.clock();var previous=before;var lastProgressAt=began
    try {
      port.signal(if(initialError>0) 1 else 2)
      while(true) {
        check(port.clock()-began<MAX_HOLD_MS) { "Continuous trial hold limit reached" }
        port.pause(FeedbackSeatRecall.GUARD_STEP_MS)
        val next=sample();val error=target-next.value
        check(port.clock()-began<=MAX_HOLD_MS) { "Continuous trial hold limit reached" }
        check(next.observedAt>=previous.observedAt) { "Trial feedback clock moved backwards" }
        check(initialError*error>=0 || abs(error)<=FeedbackSeatRecall.TOLERANCE) { "Continuous trial overshoot" }
        check(abs(error)<=abs(target-previous.value)) { "Trial moved away from target" }
        if(abs(error)<=FeedbackSeatRecall.TOLERANCE)return
        if(next.value!=previous.value)lastProgressAt=port.clock()
        check(port.clock()-lastProgressAt<=MAX_SAMPLE_AGE_MS) { "No advancing trial position" }
        previous=next
      }
    } finally { port.signal(0) }
  }
}

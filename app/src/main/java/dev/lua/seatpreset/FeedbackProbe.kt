package dev.lua.seatpreset

/** Diagnostic observations only. Neither a raw zero nor a changing value proves ECU freshness. */
internal class FeedbackProbe(val axis:String,val startedAt:Long) {
  companion object {
    const val WINDOW_MS=10_000L
    const val MAX_SAMPLES=512
    val routes=setOf("NATIVE.setting","NATIVE.bodywork","COLLECTOR.setting","COLLECTOR.bodywork","typed-adb","car-property")
    const val LIVE_GATE="Continuous movement locked: fast feedback and stopping delay need physical verification."
    fun liveMovementAllowed()=false
  }
  data class Sample(val route:String,val value:Double?,val beganAt:Long,val endedAt:Long,val cacheAtNs:Long?=null,val failure:String?=null) {
    val latency get()=endedAt-beganAt
  }
  private data class Stats(var count:Int=0,var changes:Int=0,var failures:Int=0,var last:Double?=null,val latencies:MutableList<Long> = mutableListOf())
  private val stats=linkedMapOf<String,Stats>()
  private var stopped=false
  private var accepted=0
  init { require(axis in PositionFeedback.seatAxes && startedAt>=0 && startedAt<=Long.MAX_VALUE-WINDOW_MS) }
  @Synchronized fun stop() { stopped=true }
  @Synchronized fun open(now:Long)=!stopped && now>=startedAt && now<startedAt+WINDOW_MS && accepted<MAX_SAMPLES
  @Synchronized fun accept(sample:Sample):Boolean {
    if(!open(sample.endedAt) || sample.beganAt<startedAt || sample.endedAt<sample.beganAt || sample.route !in routes) return false
    accepted++
    val s=stats.getOrPut(sample.route) { Stats() };s.count++;s.latencies+=sample.latency
    val v=sample.value?.takeIf { it.isFinite() && it in 0.0..100.0 }
    if(sample.failure!=null || v==null) s.failures++ else { if(s.last!=null && s.last!=v)s.changes++;s.last=v }
    return true
  }
  @Synchronized fun summary():String=stats.entries.joinToString("\n") { (route,s)->
    val sorted=s.latencies.sorted();"$route: ${s.count} reads, ${s.changes} changes, ${s.failures} invalid; median ${sorted[sorted.size/2]} ms; last raw ${s.last ?: "unavailable"}"
  }.ifBlank { "No accepted readings yet." }+"\nRaw SDK values may be defaults. Sample/cache times do not prove freshness. No movement path is unlocked."
}

/** Timing of observed post-release feedback, not a measurement of the physical motor stop. */
internal class PostReleaseTrace(private val axis:String,private val before:PositionFeedback.Sample,private val releasedAt:Long) {
  private var previous:PositionFeedback.Sample?=null
  private var lastObservedAt=releasedAt
  fun observe(frame:Map<String,PositionFeedback.Sample>,now:Long):String {
    val sample=frame[axis]
    if(now<lastObservedAt || sample==null)return "post-release axis=$axis accepted=false"
    val old=previous;previous=sample;lastObservedAt=now
    return "post-release axis=$axis sinceReleaseMs=${now-releasedAt} raw=${sample.value} deltaSinceBefore=${sample.value-before.value} changedSincePrevious=${old!=null && old.value!=sample.value} cacheAtNs=${sample.cacheAtNs} cacheAdvanced=${sample.cacheAtNs>(old ?: before).cacheAtNs} sampledAtMs=$now physicalStop=unverified"
  }
}

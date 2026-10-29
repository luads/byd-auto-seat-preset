package dev.lua.seatpreset

/** Distinct payload shapes. A is retained only as a labelled failed experiment. */
internal enum class SeatExperimentMethod(val label:String) {
  LEGACY("A · Integer setter · retired"), SINGLE("B · Bodywork float · one axis"), BATCH("C · Bodywork float · seat batch")
}
internal enum class SeatSdkSource(val label:String,val packageName:String) {
  NATIVE("Native MyCar SDK","com.byd.mycar"), COLLECTOR("Collector SDK · read-only","com.byd.data.collect")
}
internal object SeatExperiment {
  val axes=listOf("horizontal","backrest","height","sitpoint","legholder","headrestH","headrestV")
  // Public API identifiers reviewed in the pinned OverDrive reference, not percentage properties.
  val readIds=listOf(0x3AF00008,0x3AF00010,0x3AF00018,0x3AF00020,0x3AF00028,0x3AF00030,0x3AF00038)
  val writeIds=listOf(0x4C115010,0x4C115018,0x4C115020,0x4C115028,0x4C115030,0x4C115038,0x4C115040)
  fun requireBackend(ancestry:List<String>) {
    check(ancestry.isNotEmpty()) { "SDK dispatcher could not be inspected. Writes are unavailable." }
    check("android.hardware.bydauto.AbsTsMyCar" !in ancestry) { "SDK uses the reviewed TS integer dispatcher. Float writes are quarantined." }
  }
  fun usable(value:Double?)=value!=null && value.isFinite() && value in 2.0..125.0 && kotlin.math.abs(value-value.toFloat().toDouble())<0.0001
  fun payload(method:SeatExperimentMethod,baseline:Map<String,Double?>,delta:Int):Map<String,Float> {
    require(method!=SeatExperimentMethod.LEGACY) { "Way A is retired after coupled movement. Its aliases are not new experiments." }
    require(delta== -1 || delta==1) { "Choose exactly one numeric unit" }
    val back=baseline["backrest"]
    require(usable(back) && checkNotNull(back) in 3.0..124.0) { "No usable generic backrest reading. Check SDK/read access; zero, sentinels and unsupported values cannot be targets." }
    val selected=if(method==SeatExperimentMethod.SINGLE) listOf("backrest") else axes
    return selected.associateWith { axis ->
      val raw=baseline[axis]
      require(usable(raw)) { "Way C needs a readable $axis value. No zero or unverified sentinel will be filled in." }
      (checkNotNull(raw)+if(axis=="backrest") delta else 0).toFloat()
    }
  }
  fun ids(values:Map<String,Float>):IntArray {
    require(values.isNotEmpty() && values.keys.all { it in axes })
    return values.keys.map { writeIds[axes.indexOf(it)] }.toIntArray()
  }
  fun describe(method:SeatExperimentMethod,source:SeatSdkSource,values:Map<String,Float>) =
    "method=$method sdk=$source payload=floatArrayValue "+values.entries.joinToString("; ") { (axis,value)->"axis=$axis id=0x${writeIds[axes.indexOf(axis)].toString(16)} target=$value" }
}
internal class SeatExperimentPermit private constructor(val method:SeatExperimentMethod,val source:SeatSdkSource,
    private val baseline:Map<String,Double?>,val values:Map<String,Float>,private val requestedAtMs:Long) {
  private var used=false
  @Synchronized fun consume(now:Long,current:Map<String,Double?>,state:VehicleSnapshot,foreground:Boolean,enabled:Boolean,source:SeatSdkSource):Map<String,Float> {
    check(!used) { "Experiment already consumed" };used=true
    check(foreground && enabled && source==this.source) { "Experiment cancelled, SDK changed or movement is off" }
    check(now>=requestedAtMs && now-requestedAtMs<=RecallPolicy.MAX_AGE_MS) { "Experiment request expired" }
    check(values.keys.all { current[it]==baseline[it] }) { "Geometry changed. Read again before testing." }
    RecallPolicy.blockReason(state,now)?.let { error(it) }
    return values.toMap()
  }
  companion object {
    fun issue(method:SeatExperimentMethod,source:SeatSdkSource,baseline:Map<String,Double?>,delta:Int,requestedAtMs:Long):SeatExperimentPermit {
      require(requestedAtMs>=0)
      require(source==SeatSdkSource.NATIVE) { "Collector SDK float writes are quarantined. Its reads remain available for comparison." }
      return SeatExperimentPermit(method,source,baseline.toMap(),java.util.Collections.unmodifiableMap(LinkedHashMap(SeatExperiment.payload(method,baseline,delta))),requestedAtMs)
    }
  }
}

package dev.lua.seatpreset

/** One explicit experimental command. Never serialised, retried, or accepted by normal recall. */
internal class BackrestTestPermit private constructor(val from: Int,val target: Int,private val requestedAtMs:Long) {
  private var used=false
  @Synchronized fun consume(nowMs:Long,observed:Double,state:VehicleSnapshot,foreground:Boolean,enabled:Boolean):Int {
    check(!used) { "This test command has already been consumed" };used=true
    check(foreground && enabled) { "Test cancelled or seat movement is off" }
    check(nowMs>=requestedAtMs && nowMs-requestedAtMs<=RecallPolicy.MAX_AGE_MS) { "Test request expired" }
    check(observed==from.toDouble()) { "Backrest reading changed. Read again before testing." }
    RecallPolicy.blockReason(state,nowMs)?.let { error(it) }
    return target
  }
  companion object {
    fun issue(observed:Double,delta:Int,requestedAtMs:Long):BackrestTestPermit {
      require(delta==1 || delta== -1) { "Only one numeric unit is allowed" }
      require(observed.isFinite() && observed==observed.toInt().toDouble() && observed in 3.0..97.0) { "Backrest reading is outside the test range" }
      require(requestedAtMs>=0)
      return BackrestTestPermit(observed.toInt(),observed.toInt()+delta,requestedAtMs)
    }
  }
}

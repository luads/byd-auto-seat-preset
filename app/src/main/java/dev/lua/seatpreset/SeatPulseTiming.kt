package dev.lua.seatpreset

/** Short bounded holds, with a reserve before the target. No blind retry or learned direction. */
internal object SeatPulseTiming {
  fun duration(distance:Double,fastestRate:Double,largestStep:Double):Long {
    require(distance.isFinite() && distance>=0 && fastestRate.isFinite() && fastestRate>0 && largestStep.isFinite() && largestStep>0)
    val reserve=maxOf(2.0,largestStep)
    val planned=((distance-reserve).coerceAtLeast(0.0)/(2*fastestRate)).toLong()
    return planned.coerceIn(FeedbackSeatRecall.PULSE_MS,FeedbackSeatRecall.MAX_PULSE_MS)
  }
}

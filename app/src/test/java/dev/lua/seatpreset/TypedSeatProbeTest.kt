package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class TypedSeatProbeTest {
  @Test fun statusBearingFloatZeroIsDistinctFromAnIntegerDefault() {
    val property=0x21603b80
    assertEquals(PositionFeedback.Sample(0.0,10),TypedSeatProbe.value("horizontal",property,0,0,0f,10))
    assertThrows(IllegalStateException::class.java) { TypedSeatProbe.value("horizontal",property,0,0,0,10) }
  }
  @Test fun wrongPropertyAreaStatusTypeRangeOrTimestampCannotPass() {
    for(mode in 0..7) {
      assertThrows(IllegalStateException::class.java) {
        TypedSeatProbe.value("height",if(mode==0)0x21603b80 else 0x21603b84,if(mode==1)1 else 0,if(mode==2)1 else 0,
          when(mode) { 3->50.0;4->Float.NaN;5->101f;6->null;else->50f },if(mode==7)0 else 10)
      }
    }
    assertFalse(FeedbackProbe.liveMovementAllowed())
  }
}

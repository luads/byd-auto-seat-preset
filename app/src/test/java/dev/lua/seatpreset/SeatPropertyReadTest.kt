package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class SeatPropertyReadTest {
  private val line="event count:800, lastEvent:Property:0x21603b80,status: 0,timestamp:68022856551431,zone:0x0,floatValues: [1.0],int32Values: [],int64Values: [],bytes: [],string: "
  @Test fun floatForeAftRetainsSourceCacheTimestampWithoutClaimingFreshness() {
    val value=SeatPropertyRead.parse(line)!!;assertEquals(1.0,value.percent,0.0);assertEquals(68022856551431L,value.cacheAtNs)
  }
  @Test fun failedWrongPropertyAreaOrShapeCannotBecomeCoordinate() {
    for(raw in listOf("",line.replace("status: 0","status: 1"),line.replace("3b80","3b84"),line.replace("zone:0x0","zone:0x1"),line.replace("[1.0]","[]"),line.replace("[1.0]","[1.0,2.0]"),line.replace("[1.0]","[NaN]"),line.replace("[1.0]","[101.0]"),line.replace("68022856551431","0"))) assertNull(SeatPropertyRead.parse(raw))
  }
  @Test fun commandIsFixedReadOnlyAndDoesNotWriteASettingOrProperty() {
    assertTrue(SeatPropertyRead.COMMAND.startsWith("dumpsys -t 1 car_service"));assertFalse(SeatPropertyRead.COMMAND.contains("set-property"));assertFalse(SeatPropertyRead.COMMAND.contains("settings put"))
  }
}

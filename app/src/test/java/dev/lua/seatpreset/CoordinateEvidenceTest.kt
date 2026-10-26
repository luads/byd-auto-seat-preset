package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class CoordinateEvidenceTest {
  @Test fun preservesEvidenceAcrossPersistenceWithoutPromotingFreshness() {
    val evidence=CoordinateEvidence("car-service.0x21603b80",700000,100000000000)
    val position=Position(mapOf("horizontal" to 40.0),"fixture",SeatObservation.FORMAT,mapOf("horizontal" to evidence))
    val doc=PresetCodec.defaults().let { it.copy(presets=it.presets.mapIndexed { i,p->if(i==0) p.copy(position=position) else p }) }
    val decoded=PresetCodec.decode(PresetCodec.encode(doc))
    assertEquals(doc,decoded)
    assertTrue(evidence.describe().contains("freshness=unverified"))
    assertTrue(evidence.describe().contains("cacheClock=unverified"))
    assertTrue(evidence.describe().contains("assumedElapsedAgeMs=600000"))
  }
  @Test fun legacyPositionsKeepTheirCoordinatesWithUnknownEvidence() {
    val position=Position(mapOf("height" to 40.0),"fixture",SeatObservation.FORMAT)
    val doc=PresetCodec.defaults().let { it.copy(presets=it.presets.mapIndexed { i,p->if(i==0) p.copy(position=position) else p }) }
    assertEquals(doc,PresetCodec.decode(PresetCodec.encode(doc)))
    assertTrue(SeatDiagnostics.position("saved",position).contains("route=legacy-unknown"))
  }
  @Test fun malformedMetadataCannotBecomeAValidPosition() {
    for(evidence in listOf(CoordinateEvidence("route",-1),CoordinateEvidence("route",1,0),CoordinateEvidence("bad\nroute",1))) {
      val doc=PresetCodec.defaults().let { it.copy(presets=it.presets.mapIndexed { i,p->if(i==0) p.copy(position=Position(mapOf("height" to 40.0),"fixture","format",mapOf("height" to evidence))) else p }) }
      assertThrows(IllegalArgumentException::class.java) { PresetCodec.encode(doc) }
    }
  }
}

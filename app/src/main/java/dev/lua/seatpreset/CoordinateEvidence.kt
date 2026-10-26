package dev.lua.seatpreset

/** Sample times are diagnostic provenance, never proof that an OEM value is current. */
data class CoordinateEvidence(val route: String, val sampledAtMs: Long, val cacheAtNs: Long? = null) {
  fun describe(): String = "route=$route sampledAtMs=$sampledAtMs cacheAtNs=${cacheAtNs ?: "unknown"} " +
    "cacheClock=unverified freshness=unverified" +
    (cacheAtNs?.let { " assumedElapsedAgeMs=${sampledAtMs - it / 1_000_000}" } ?: "")
}

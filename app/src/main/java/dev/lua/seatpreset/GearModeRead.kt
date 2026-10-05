package dev.lua.seatpreset

import org.json.JSONObject

/** Measured DiLink-5 codes. Recent reads do not establish underlying telemetry freshness. */
internal object GearModeRead {
  const val SETTING = "sys.share.gpack.agent.data"
  fun fromJson(row: String?): Int? = runCatching {
    if (row.isNullOrBlank() || row.length > 16_384) return null
    val value = JSONObject(row).opt("gearMode")
    // Reject coercions, decimal values and unknown codes instead of inventing a state.
    (value as? Int)?.takeIf { it in 1..4 }
  }.getOrNull()
  fun parked(gearMode: Int?, epbState: Int?) = gearMode == 1 && epbState == 3
}

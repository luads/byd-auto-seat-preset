package dev.lua.seatpreset

object PanelVisibility {
  fun allowed(home: Boolean, enabled: Boolean, snapshot: VehicleSnapshot, now: Long, parkOnly: Boolean = true): Boolean {
    if (!home || !enabled) return false
    if (!parkOnly) return true
    val sampled = snapshot.sampledAtMs ?: return false
    return snapshot.gear == Gear.P && sampled >= 0 && now >= 0 && sampled <= now && now - sampled <= RecallPolicy.MAX_AGE_MS
  }
}

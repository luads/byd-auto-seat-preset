package dev.lua.seatpreset

object PanelVisibility {
  // Visibility only: does not promote a cached setting to fresh movement evidence.
  fun reportedPark(home: Boolean, enabled: Boolean, interactive: Boolean, gearMode: Int?) =
    home && enabled && interactive && gearMode == 1
  fun allowed(home: Boolean, enabled: Boolean, snapshot: VehicleSnapshot, now: Long, parkOnly: Boolean = true): Boolean {
    if (!home || !enabled) return false
    if (!parkOnly) return true
    val sampled = snapshot.sampledAtMs ?: return false
    return snapshot.gear == Gear.P && sampled >= 0 && now >= 0 && sampled <= now && now - sampled <= RecallPolicy.MAX_AGE_MS
  }
}

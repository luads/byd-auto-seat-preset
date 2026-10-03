package dev.lua.seatpreset

internal object UpdatePolicy {
  const val RETRY_MS = 60_000L
  fun shouldCheck(enabled: Boolean, unlocked: Boolean, boot: Int?, completedBoot: Int?, now: Long, attempted: Long?): Boolean =
    enabled && unlocked && (boot == null || boot != completedBoot) &&
      (attempted == null || now < attempted || now - attempted >= RETRY_MS)
  fun installSucceeded(exit: Int, output: String) = exit == 0 && output.lineSequence().any { it.trim() == "Success" }
}

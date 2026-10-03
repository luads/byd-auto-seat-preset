package dev.lua.seatpreset

/** Null is unknown, including a failed preference read. No grant implies consent. */
internal object StartupPolicy {
  fun migrate(unlocked: Boolean, current: () -> Boolean?, legacy: () -> Boolean?, save: (Boolean) -> Boolean): Boolean? {
    val known = current()
    if (known != null || !unlocked) return known
    val chosen = legacy() ?: false
    check(save(chosen)) { "Bootstrap policy persistence failed" }
    return chosen
  }
  fun needsReassert(armed: Boolean?, open: Boolean?, permission: Boolean, adb: String?, wifi: String?) =
    armed == true && open == false && permission && (adb == "0" || wifi == "0")
}

/** Coalesces arbitrary callbacks to one running pass and at most one subsequent pass. */
internal class RetryGate {
  private var running = false
  private var pending = false
  @Synchronized fun request(): Boolean {
    if (running) { pending = true; return false }
    running = true; return true
  }
  @Synchronized fun finish(): Boolean {
    if (pending) { pending = false; return true }
    running = false; return false
  }
}

internal class StartupTimes(val processStartedAtMs: Long) {
  var storageReadyAtMs: Long? = null; private set
  var sampledAtMs: Long? = null; private set
  fun storageReady(now: Long) { if (storageReadyAtMs == null) storageReadyAtMs = now }
  fun sampled(now: Long) { sampledAtMs = now }
  // No phone identity contract exists. Neither readiness nor a recent getter establishes one.
  val freshUnlockIdentity: Boolean get() = false
}

internal object CredentialAccess {
  fun <T> read(unlocked: Boolean, action: () -> T): T {
    check(unlocked) { "Android storage is locked or unavailable" }
    return action()
  }
}

/** SharedPreferences mutates memory even on a failed commit. That is not saved authorization. */
internal class RecoveryChoice {
  private var pending: Boolean? = null
  @Synchronized fun read(disk: () -> Boolean?): Boolean? = if (pending != null) null else disk()
  @Synchronized fun save(value: Boolean, commit: (Boolean) -> Boolean) {
    pending = value
    check(commit(value)) { "Could not persist startup choice" }
    pending = null
  }
  @Synchronized fun retry(commit: (Boolean) -> Boolean) { pending?.let { save(it, commit) } }
}

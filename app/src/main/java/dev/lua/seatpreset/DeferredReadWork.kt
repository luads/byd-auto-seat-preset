package dev.lua.seatpreset

/** Named read-only jobs replace themselves, never enqueue movement or user setup commands. */
internal class DeferredReadWork(private val limit: Int = 8) {
  private val pending = linkedMapOf<String, () -> Unit>()
  @Synchronized fun offer(name: String, read: () -> Unit) {
    check(name in pending || pending.size < limit) { "Read retry limit reached" }
    pending[name] = read
  }
  @Synchronized fun retry(unlocked: Boolean, authenticated: Boolean?) {
    if (!unlocked || authenticated != true) return
    val done = pending.filterValues { runCatching(it).isSuccess }.keys
    done.forEach(pending::remove)
  }
}

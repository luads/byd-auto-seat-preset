package dev.lua.seatpreset

/** An in-memory fallback until the existing log file is writable. Private/public queues are separate. */
class DeferredLogBuffer(private val maxLines: Int = 200, private val maxChars: Int = 128_000) {
  private val pending = ArrayDeque<String>()
  private var chars = 0
  private var dropped = 0

  @Synchronized
  fun clear() {
    pending.clear(); chars = 0; dropped = 0
  }

  @Synchronized
  fun append(line: String, write: (String) -> Unit): Boolean {
    if (line.length > maxChars) dropped++ else {
      pending.addLast(line)
      chars += line.length
    }
    while (pending.size > maxLines || chars > maxChars) {
      chars -= pending.removeFirst().length
      dropped++
    }
    return flush(write)
  }

  @Synchronized
  fun flush(write: (String) -> Unit): Boolean {
    if (pending.isEmpty() && dropped == 0) return true
    val text = (if (dropped > 0) "log-buffer: $dropped early line(s) dropped at buffer limit\n" else "") +
      pending.joinToString("\n", postfix = if (pending.isEmpty()) "" else "\n")
    if (runCatching { write(text) }.isFailure) return false
    pending.clear(); chars = 0; dropped = 0
    return true
  }
}

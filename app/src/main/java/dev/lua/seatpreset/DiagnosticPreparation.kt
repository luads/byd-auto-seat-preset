package dev.lua.seatpreset

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** One process-owned export queue, held busy until its owner callback returns. */
internal class DiagnosticPreparation<T>(private val executor: Executor) {
  private val busy = AtomicBoolean(false)
  fun submit(work: () -> T, dispatch: (() -> Unit) -> Unit, completed: (Result<T>) -> Unit): Boolean {
    if (!busy.compareAndSet(false, true)) return false
    try {
      executor.execute {
        val result = runCatching(work)
        try {
          dispatch {
            try { completed(result) } finally { busy.set(false) }
          }
        } catch (_: Throwable) { busy.set(false) }
      }
    } catch (_: Throwable) { busy.set(false); return false }
    return true
  }
}

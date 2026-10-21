package dev.lua.seatpreset

/** Only getter connection establishment is retried; a setter must never enter this helper. */
internal object GetterReadiness {
  fun <T> read(pause: ()->Unit = { Thread.sleep(150) }, getter: ()->T): T {
    repeat(5) { attempt ->
      try { return getter() } catch(e: Exception) {
        val connecting=generateSequence(e as Throwable?) { it.cause }.take(5).any { it.message?.equals("not connected",ignoreCase=true)==true }
        if(!connecting || attempt==4) throw e
        pause()
      }
    }
    error("Getter readiness unavailable")
  }
}

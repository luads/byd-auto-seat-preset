package dev.lua.seatpreset

import java.util.concurrent.atomic.AtomicLong

/** Process-memory ownership only. No persisted/replayed requests or background scheduler. */
internal object SeatMotionState {
  private val serial=AtomicLong()
  val operations=MotionOperationGate()
  @Volatile var mainVisible=false
  @Volatile var panelVisible=false
  @Volatile var progress=""
  fun token()=serial.get()
  fun current(token:Long)=serial.get()==token
  fun cancel() { serial.incrementAndGet();progress="Stopping seat load…" }
  fun surface(origin:RecallOrigin,context:android.content.Context):Boolean = when(origin) {
    RecallOrigin.CARD,RecallOrigin.EDITOR -> mainVisible
    RecallOrigin.PANEL -> panelVisible
    RecallOrigin.CHILD_LOCK -> HomeVisibilityService.instance!=null && ChildBindings(context).enabled
    RecallOrigin.WIDGET -> false
  }
}

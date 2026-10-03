package dev.lua.seatpreset

import android.view.View
import android.view.ViewGroup

object PanelMotion {
  const val IN_MS = 180L
  const val OUT_MS = 140L
  fun interactive(view: View, enabled: Boolean) {
    view.isEnabled = enabled
    if (view is ViewGroup) for (i in 0 until view.childCount) interactive(view.getChildAt(i), enabled)
  }
  fun show(view: View) {
    view.animate().cancel(); view.visibility = View.VISIBLE; view.alpha = 0f
    interactive(view, true)
    view.animate().alpha(1f).setDuration(IN_MS).withEndAction(null).start()
  }
  fun hide(view: View, removed: () -> Unit) {
    interactive(view, false)
    view.animate().cancel()
    view.animate().alpha(0f).setDuration(OUT_MS).withEndAction(removed).start()
  }
}

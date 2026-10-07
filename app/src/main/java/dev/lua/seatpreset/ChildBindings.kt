package dev.lua.seatpreset

import android.content.Context

@android.annotation.SuppressLint("ApplySharedPref")
class ChildBindings(context: Context) {
  private val prefs get() = StorageAccess.prefs(context, "child-bindings")
  private val context = context.applicationContext
  fun preset(side: ChildSide): Preset? {
    val id = prefs.getString(side.name, null) ?: return null
    return PresetStore(context).all().firstOrNull { it.id == id }
  }
  fun assign(side: ChildSide, preset: Preset?) {
    if (preset != null) require(PresetStore(context).all().any { it.id == preset.id })
    check(prefs.edit().apply { if (preset == null) remove(side.name) else putString(side.name, preset.id) }.commit())
  }
}

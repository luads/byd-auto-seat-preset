package dev.lua.seatpreset

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

@android.annotation.SuppressLint("ApplySharedPref")
class HomePanelSettings(private val context: Context) {
  private val prefs = StorageAccess.prefs(context, "home-panel")
  var enabled: Boolean
    get() = prefs.getBoolean("enabled", false)
    set(value) { check(prefs.edit().putBoolean("enabled", value).commit()) }
  var parkOnly: Boolean
    get() = prefs.getBoolean("park-only", true)
    set(value) { check(prefs.edit().putBoolean("park-only", value).commit()) }
  fun observerName() = ComponentName(context, HomeVisibilityService::class.java).flattenToString()
  fun observerEnabled() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == ComponentName(context, HomeVisibilityService::class.java) }
  fun setObserver(on: Boolean) {
    check(Startup.canWrite(context)) { "Startup permission is needed" }
    val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    val current = Settings.Secure.getString(context.contentResolver, key)
    val normalized = current.orEmpty().split(':').filter { it.isNotBlank() }.joinToString(":") { ComponentName.unflattenFromString(it)?.flattenToString() ?: it }
    check(Settings.Secure.putString(context.contentResolver, key, HomePanelPolicy.services(normalized, observerName(), on)))
    if (on) check(Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1))
    check(observerEnabled() == on) { "Home detection permission did not apply" }
    WidgetDiagnostics.record(context, "home observer enabled=$on")
  }
}

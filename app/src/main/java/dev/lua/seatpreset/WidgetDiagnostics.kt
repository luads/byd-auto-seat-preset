package dev.lua.seatpreset

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.io.File

/** Local, bounded widget lifecycle evidence. No driver names or vehicle data. */
object WidgetDiagnostics {
  private val log = CredentialLog("widget-diagnostics.log", 16_000)
  fun record(context: Context, event: String) { log.append(context, event) }
  fun flush(context: Context) { log.flush(context) }
  fun report(context: Context): String = runCatching {
    StorageAccess.requireUnlocked(context)
    flush(context)
    val manager = AppWidgetManager.getInstance(context)
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val launcher = context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: "unknown"
    val ids = manager.getAppWidgetIds(ComponentName(context, PresetWidget::class.java))
    val sizes = ids.joinToString("\n") { id ->
      val options = manager.getAppWidgetOptions(id)
      "id=$id size=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}..${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} × ${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}..${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} dp"
    }
    val file = File(context.filesDir, "widget-diagnostics.log")
    val recent = if (file.exists()) file.readLines().takeLast(16).joinToString("\n") else "No widget events recorded"
    "Version: ${BuildConfig.VERSION_NAME} · ${BuildConfig.FLAVOR}\nHome: $launcher\nPin supported: ${manager.isRequestPinAppWidgetSupported}\nBound widgets: ${ids.size}\n$sizes\n\n$recent"
  }.getOrElse { "Widget diagnostics unavailable: ${it.javaClass.simpleName}" }
}

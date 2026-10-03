package dev.lua.seatpreset

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

class PresetWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
  override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) { refresh(context) }
  override fun onReceive(context: Context, intent: Intent) {
    WidgetDiagnostics.record(context, "receive=${intent.action}")
    if (intent.action == "${context.packageName}.PIN_CONFIRMED") {
      WidgetDiagnostics.record(context, "pin confirmation callback received")
      refresh(context)
      return
    }
    super.onReceive(context, intent)
    if (intent.action == "${context.packageName}.RECALL" && StorageAccess.unlocked(context)) {
      val id = intent.getStringExtra("preset-id") ?: return
      val preset = runCatching { PresetStore(context).all().firstOrNull { it.id == id } }.getOrNull() ?: return
      val result = Vehicle.recall(context, preset)
      Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
    }
  }
  companion object {
    fun views(context: Context, widgetId: Int): RemoteViews {
      val store = PresetStore(context)
      val views = RemoteViews(context.packageName, R.layout.preset_widget)
      views.setTextViewText(R.id.widget_title, if (BuildConfig.DEMO) "Seat presets · Demo" else "Seat presets")
      views.setTextViewText(R.id.widget_result, store.result)
      val height = if (widgetId == 0) 220 else AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 196)
      val compact = height < 184
      for (id in listOf(R.id.favourite_one_art, R.id.favourite_two_art)) {
        views.setViewVisibility(id, if (compact) android.view.View.GONE else android.view.View.VISIBLE)
      }
      views.setInt(R.id.widget_result, "setMaxLines", if (compact) 1 else 2)
      store.favourites().forEachIndexed { slot, preset ->
        val nameId = if (slot == 0) R.id.favourite_one else R.id.favourite_two
        val targetId = if (slot == 0) R.id.favourite_one_target else R.id.favourite_two_target
        views.setTextViewText(nameId, preset.name)
        views.setBoolean(targetId, "setEnabled", preset.position != null)
        views.setContentDescription(targetId, "Recall preset for ${preset.name}")
        val intent = Intent(context, PresetWidget::class.java).apply {
          action = "${context.packageName}.RECALL"
          data = android.net.Uri.parse("seatpreset://recall/${preset.id}/$widgetId/$slot")
          putExtra("preset-id", preset.id)
        }
        views.setOnClickPendingIntent(targetId, PendingIntent.getBroadcast(context, slot, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      }
      if (store.favourites().any { it.position == null }) views.setTextViewText(R.id.widget_result, "Save a position in the app to use a favourite")
      val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      views.setOnClickPendingIntent(R.id.widget_title, open)
      return views
    }
    fun refresh(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, PresetWidget::class.java))
      WidgetDiagnostics.record(context, "refresh bound=${ids.joinToString()}")
      ids.forEach { id ->
        runCatching { manager.updateAppWidget(id, views(context, id)) }
          .onSuccess { WidgetDiagnostics.record(context, "update sent id=$id") }
          .onFailure { WidgetDiagnostics.record(context, "update failed id=$id ${it.javaClass.simpleName}") }
      }
    }
  }
}

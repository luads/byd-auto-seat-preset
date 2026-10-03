package dev.lua.seatpreset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class StartupReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) Startup.retry(context)
  }
}

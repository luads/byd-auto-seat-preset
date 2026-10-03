package dev.lua.seatpreset

import android.content.Context
import android.os.UserManager

/** Android credential storage, never the identity of the phone that opened the car. */
object StorageAccess {
  fun unlocked(context: Context): Boolean = runCatching {
    context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
  }.getOrDefault(false)
  fun requireUnlocked(context: Context) { check(unlocked(context)) { "Android storage is locked or unavailable" } }
  fun prefs(context: Context, name: String): android.content.SharedPreferences {
    return CredentialAccess.read(unlocked(context)) { context.getSharedPreferences(name, Context.MODE_PRIVATE) }
  }
}

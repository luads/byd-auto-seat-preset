package dev.lua.seatpreset

object HomePanelPolicy {
  fun isHome(homePackage: String?, homeClass: String?, eventPackage: String?, eventClass: String?): Boolean =
    !homePackage.isNullOrBlank() && !homeClass.isNullOrBlank() && homePackage == eventPackage && homeClass == eventClass
  fun services(current: String?, ours: String, enabled: Boolean): String =
    current.orEmpty().split(':').filter { it.isNotBlank() && it != ours }.toMutableList().apply {
      if (enabled) add(ours)
    }.distinct().joinToString(":")
}

package dev.lua.seatpreset

/** Reports only observed numeric changes, never actuation success or physical completion. */
internal object BackrestTestReadback {
  fun describe(before:Map<String,Double?>,after:Map<String,Double?>,target:Double):String {
    val routes=(before.keys+after.keys).sorted()
    val changes=routes.filter { it!="setting.backrest" && before[it]!=null && after[it]!=null && before[it]!=after[it] }
    val values=routes.joinToString("; ") { route ->
      val a=before[route];val b=after[route]
      "route=$route before=$a after=$b delta=${if(a!=null && b!=null) b-a else "unknown"}"
    }
    return "targetReported=${after["setting.backrest"]==target} otherChangedRoutes=$changes observations=[$values] units=unverified cacheFreshness=unverified physicalCompletion=unverified"
  }
}

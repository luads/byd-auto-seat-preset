package dev.lua.seatpreset
import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.graphics.Color

/** Local trace replay in both flavours. No SDK, polling, stored input or movement callback. */
class ChildReplayActivity: Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!StorageAccess.unlocked(this)) { finish(); return }
    if (!PresetStore(this).developerMode) { finish();return }
    val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,24,32,32);setBackgroundColor(getColor(R.color.matte_canvas))}
    fun text(value:String,size:Float)=TextView(this).apply{text=value;textSize=size;setTextColor(getColor(R.color.matte_silver));setPadding(0,12,0,12)}
    page.addView(text("Child-lock trace replay",28f))
    page.addView(text("Recorded state patterns only. No seat action or live vehicle reads.",16f))
    val output=text("Choose a measured trace",22f)
    val row=LinearLayout(this)
    listOf("Left · 100 ms" to (ChildSide.LEFT to listOf(100L)),"Right · 302 ms" to (ChildSide.RIGHT to listOf(302L)),"Left · 303 ms" to (ChildSide.LEFT to listOf(303L)),"Right · two pairs" to (ChildSide.RIGHT to listOf(300L,605L))).forEach { (name,scenario) ->
      row.addView(Button(this).apply { text=name;isAllCaps=false;setOnClickListener {
        val r=ChildLockRecognizer();val frames=ChildReplay.samples(scenario.first,scenario.second);val events=frames.mapNotNull(r::sample)
        output.text="${events.size} ${scenario.first.name.lowercase()} gesture${if(events.size==1) "" else "s"} detected · ${frames.size} samples\nOne-second pair timeout; 250 ms maximum sampling gap. These limits need parked trials."
      } },LinearLayout.LayoutParams(0,64,1f))
    }
    page.addView(row);page.addView(output)
    page.addView(Button(this).apply{text="Back to settings";isAllCaps=false;setOnClickListener{finish()}})
    setContentView(ScrollView(this).apply{addView(page)})
  }
}

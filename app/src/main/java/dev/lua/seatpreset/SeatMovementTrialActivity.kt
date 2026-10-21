package dev.lua.seatpreset

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Only an actual tap on Move creates consent. Opening this screen never sends a command. */
class SeatMovementTrialActivity : Activity() {
  private val worker=Executors.newSingleThreadExecutor()
  private val busy=AtomicBoolean(false)
  private val consent=ManualSeatRecall()
  @Volatile private var foreground=false
  private var generation=0
  private lateinit var bridge: OemReadBridge
  private lateinit var output: TextView
  private lateinit var move: Button
  private val choices=linkedMapOf<String,CheckBox>()
  private var target: Position?=null
  private var presetName="Preset"
  private var presetId=""
  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    if(!StorageAccess.unlocked(this) || BuildConfig.DEMO) { finish();return }
    val preset=PresetStore(this).all().firstOrNull { it.id==intent.getStringExtra("preset-id") }
    if(preset?.position==null) { Toast.makeText(this,"Hold a preset card to save the current position",Toast.LENGTH_LONG).show();finish();return }
    target=preset.position;presetName=preset.name;presetId=preset.id;bridge=OemReadBridge(this)
    val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(36,28,36,28);setBackgroundColor(getColor(R.color.matte_canvas)) }
    fun label(value:String,size:Float)=TextView(this).apply { text=value;textSize=size;setTextColor(getColor(R.color.matte_silver));setPadding(0,12,0,12) }
    page.addView(label("Move to ${preset.name}",28f))
    page.addView(label("Confirm P, parking brake and clear space. Stay at the native seat controls. Only selected driver-seat axes move. Use native controls if movement is wrong; app cancellation is not validated.",18f))
    output=label("Reading current position…",21f);page.addView(output)
    SeatObservation.axes.forEach { axis -> choices[axis]=CheckBox(this).apply { text=axis;setTextColor(getColor(R.color.matte_silver));minHeight=64;isEnabled=false;page.addView(this) } }
    move=Button(this).apply { text="Move now";isAllCaps=false;isEnabled=false;setOnClickListener { send() } };page.addView(move)
    page.addView(Button(this).apply { text="Adjust saved target";isAllCaps=false;setOnClickListener { adjustTarget() } })
    page.addView(Button(this).apply { text="Read again";isAllCaps=false;setOnClickListener { read() } })
    page.addView(Button(this).apply { text="Cancel";isAllCaps=false;setOnClickListener { finish() } })
    setContentView(ScrollView(this).apply { addView(page) })
  }
  private fun task(work:()->Unit) {
    if(!foreground || !busy.compareAndSet(false,true)) return
    val token=generation
    worker.execute { try { work() } catch(e:Exception) {
      consent.cancel()
      runOnUiThread { if(foreground && token==generation) { output.text="Stopped: ${e.cause?.message ?: e.message ?: e.javaClass.simpleName}";move.isEnabled=false } }
    } finally { busy.set(false) } }
  }
  private fun read() { task {
    val current=bridge.seat();val plan=ManualSeatRecall.plan(current,checkNotNull(target));val parked=bridge.parkedVetoClear();val token=generation
    runOnUiThread { if(foreground && token==generation) {
      choices.forEach { (axis,box) ->
        val item=plan.firstOrNull { it.name==axis }
        box.text=if(item==null) "$axis · reading unavailable; skipped" else "$axis: ${item.from} → ${item.to}"
        box.isEnabled=item!=null;box.isChecked=item!=null && item.from!=item.to
      }
      move.isEnabled=parked && plan.any { it.from!=it.to }
      output.text=when { !parked -> "Select P and apply the parking brake, then Read again."; plan.none { it.from!=it.to } -> "Available axes already match. Save a different position with a long press on its card, then return here."; else -> "Review the saved target, then tap Move now. Fore/aft is skipped if its reading is unavailable." }
    } }
  } }
  private fun adjustTarget() {
    if(busy.get()) return
    val axes=choices.filterValues { it.isEnabled }.keys.toList()
    if(axes.isEmpty()) { output.text="Read an available axis first";return }
    android.app.AlertDialog.Builder(this).setTitle("Adjust saved target").setItems(axes.toTypedArray()) { _,i ->
      val axis=axes[i]
      val picker=NumberPicker(this).apply { minValue=1;maxValue=99;value=checkNotNull(target).coordinates.getValue(axis).toInt() }
      android.app.AlertDialog.Builder(this).setTitle("$axis target (%)").setView(picker)
        .setNegativeButton("Cancel",null).setPositiveButton("Save target") { _,_->
          runCatching {
            val updated=checkNotNull(target).let { it.copy(coordinates=it.coordinates+(axis to picker.value.toDouble())) }
            val store=PresetStore(this)
            store.save(store.all().map { if(it.id==presetId) it.copy(position=updated) else it })
            target=updated;read()
          }.onFailure { output.text="Target could not be saved" }
        }.show()
    }.show()
  }
  private fun send() {
    if(busy.get() || !foreground) return
    if(!SeatControl.enabled(this)) { output.text="Enable Seat movement in Settings first";return }
    val selected=choices.filterValues { it.isEnabled && it.isChecked }.keys.toSet()
    if(selected.isEmpty()) { output.text="Select an axis to move";return }
    move.isEnabled=false
    Vehicle.requestRecall(this,Preset(presetId,presetName,checkNotNull(target)),axes=selected) { result ->
      if(foreground) output.text=result.message
    }
  }

  override fun onResume() { super.onResume();foreground=true;read() }
  override fun onStop() { foreground=false;generation++;consent.cancel();super.onStop() }
  override fun onDestroy() { worker.shutdownNow();super.onDestroy() }
}

package dev.lua.seatpreset

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
  private var settingsView: ComposeView? = null
  private val settingsState = mutableStateOf<SettingsUiState?>(null)
  private val backgroundColor = Color.rgb(20, 23, 27)
  private val panelColor = Color.rgb(32, 37, 43)
  private val textColor = Color.rgb(229, 233, 239)
  private val mutedColor = Color.rgb(168, 179, 193)
  private val accentColor = Color.rgb(90, 168, 237)
  private val worker = Executors.newSingleThreadExecutor()
  private lateinit var store: PresetStore
  private lateinit var page: LinearLayout
  private var panelSetupMessage = ""
  private var panelSetupBusy = false
  private var settings = false
  private var demoControls = false
  private var release: PublicRelease? = null
  private var updateMessage = "Check for a public release"
  private var updateBusy = false
  private var captureBusy = false
  @Volatile private var captureSession = 0

  private val unlockedReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) { Startup.retry(context); safeRender() }
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    runCatching { registerReceiver(unlockedReceiver, android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED)) }
    store = PresetStore(this)
    settings = savedInstanceState?.getBoolean("settings") ?: false
    demoControls = savedInstanceState?.getBoolean("demoControls") ?: false
    safeRender()
    runCatching { if (StorageAccess.unlocked(this) && store.checkUpdatesOnStart) checkUpdates() }
  }
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent); setIntent(intent)
  }
  @Deprecated("Legacy Android back dispatch")
  override fun onBackPressed() {
    if (settings || demoControls) { settings=false;demoControls=false;safeRender() }
    else super.onBackPressed()
  }
  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean("settings", settings)
    outState.putBoolean("demoControls", demoControls)
    super.onSaveInstanceState(outState)
  }
  override fun onResume() { super.onResume(); HomeVisibilityService.instance?.hide(); Startup.retry(this); if (::store.isInitialized) safeRender() }
  override fun onStop() { captureSession++; super.onStop() }
  override fun onDestroy() { runCatching { unregisterReceiver(unlockedReceiver) }; worker.shutdownNow(); super.onDestroy() }

  private fun safeRender() { render() }
  private fun render() { runCatching { renderReady() }.onFailure {
    settingsView?.disposeComposition();settingsView=null
    setContentView(android.widget.TextView(this).apply {
      text = getString(R.string.storage_not_ready); setPadding(32, 32, 32, 32)
      setOnClickListener { Startup.retry(this@MainActivity); safeRender() }
    })
  } }
  private fun renderReady() {
    StorageAccess.requireUnlocked(this)
    if(settings) { renderSettings();return }
    settingsView?.disposeComposition();settingsView=null
    page = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(32), dp(40), dp(32), dp(40))
      setBackgroundColor(backgroundColor)
    }
    val header = row()
    header.addView(text(if (settings) "Settings" else if (demoControls) "Demo controls" else "Seat Presets", 28f), LinearLayout.LayoutParams(0, -2, 1f))
    header.addView(action(if (settings || demoControls) "Presets" else "Settings") { if (settings || demoControls) { settings = false; demoControls = false } else settings = true; render() })
    page.addView(header)
    page.addView(text(if (BuildConfig.DEMO) "DEMO · simulated vehicle, no physical movement" else "PRE-ALPHA · two favourite driving presets", 15f, mutedColor))
    if (demoControls && BuildConfig.DEMO) renderDemo() else renderPresets()
    val frame = FrameLayout(this).apply {
      addView(page, FrameLayout.LayoutParams(dp(minOf(resources.configuration.screenWidthDp, 1280)), -2, android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL))
    }
    setContentView(ScrollView(this).apply { setBackgroundColor(backgroundColor); addView(frame) })
  }

  private fun renderPresets() {
    val vehicle = Vehicle.adapter(this)
    val block = if (BuildConfig.DEMO) RecallPolicy.blockReason(vehicle.snapshot(), android.os.SystemClock.elapsedRealtime()) else if(!SeatWritePolicy.allowed()) SeatWritePolicy.BLOCK_REASON else if(SeatControl.enabled(this)) "Hold to save. Tap to load in P." else "Seat movement is off. Enable it in Settings."
    page.addView(text(block ?: "P · parking brake confirmed", 16f, if (block == null) getColor(R.color.matte_ready) else getColor(R.color.matte_caution)))
    val wide = resources.configuration.screenWidthDp >= 600
    val favourites = store.favourites()
    (favourites + store.all().filter { p -> favourites.none { it.id == p.id } }).chunked(if (wide) 2 else 1).forEach { pair ->
      val cards = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
      pair.forEach { preset ->
        val saved = preset.position != null
        val card = LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x335AA8ED), rounded(panelColor, 28), null)
          setPadding(dp(28), dp(24), dp(28), dp(28))
          isClickable = true; isFocusable = true; isLongClickable = true
          contentDescription = "${preset.name}. Hold to save current position; tap to use saved position."
          setOnLongClickListener { capture(preset); true }
          setOnClickListener {
            runCatching {
              if (!saved) toast("Hold this card to save the current position")
              else if (BuildConfig.DEMO) { toast(Vehicle.recall(this@MainActivity, preset).message); render() }
              else Vehicle.requestRecall(this@MainActivity,preset) { result -> if(!isDestroyed) { toast(result.message);render() } }
            }.onFailure { toast(it.message ?: "Action failed") }
          }
        }
        val heading = row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        heading.addView(text(preset.name, 32f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(action("Edit") { editPreset(preset) }.apply {
          contentDescription = "Edit ${preset.name}"
          background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x335AA8ED), rounded(Color.TRANSPARENT, 14), null)
          setTextColor(mutedColor)
        })
        card.addView(heading)
        card.addView(text(if (favourites.any { it.id == preset.id }) "FAVOURITE" else "PRESET", 12f, mutedColor))
        card.addView(ImageView(this).apply {
          setImageResource(R.drawable.seat_matte); scaleType = ImageView.ScaleType.FIT_CENTER
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
          setPadding(0, dp(16), 0, dp(16))
        }, LinearLayout.LayoutParams(-1, dp(if (wide) 240 else 180)))
        val footer = row().apply { gravity = android.view.Gravity.CENTER_VERTICAL; if (!wide) orientation = LinearLayout.VERTICAL }
        footer.addView(text(if (saved) "Position saved" else "No position saved", 16f, mutedColor), if (wide) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
        footer.addView(text(if (saved) { if (BuildConfig.DEMO) "Use preset  →" else "Use preset  →" } else "Hold to save", 18f, accentColor))
        card.addView(footer)
        val params = if (wide) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2)
        params.setMargins(dp(6), dp(24), dp(6), dp(16))
        cards.addView(card, params)
      }
      page.addView(cards)
    }
    val result = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = rounded(panelColor, 14)
      setPadding(dp(20), dp(8), dp(20), dp(12))
      addView(text("Last request", 13f, mutedColor))
      addView(text(store.result, 18f))
    }
    if (BuildConfig.DEMO && !store.result.startsWith("Select either favourite")) page.addView(result)
    store.storageMessage()?.let { page.addView(text(it, 16f, getColor(R.color.matte_caution))) }
    val actions = row().apply { if (!wide) orientation = LinearLayout.VERTICAL }
    actions.addView(action("Add preset") { nameDialog("New preset", "") { name -> store.save(store.all() + Preset(UUID.randomUUID().toString(), name)); changed() } })
    actions.addView(action("Home panel") { settings = true; render() })
    if (BuildConfig.DEMO && store.developerMode) actions.addView(action("Demo controls") { demoControls = true; render() })
    for (index in 0 until actions.childCount) {
      actions.getChildAt(index).layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12); bottomMargin = dp(8) }
    }
    page.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
  }

  private fun renderDemo() {
    val vehicle = DemoVehicle(this)
    page.addView(text("SIMULATED VEHICLE", 13f, mutedColor))
    val gears = row()
    Gear.values().forEach { gear -> gears.addView(action(gear.name) {
      vehicle.configure(gear.name, vehicle.brake(), vehicle.freshness(), vehicle.position()); render()
    }.apply { isSelected = vehicle.gear() == gear.name }, LinearLayout.LayoutParams(0, -2, 1f)) }
    page.addView(gears)
    page.addView(Switch(this).apply {
      text = "Parking brake confirmed"; setTextColor(textColor); minHeight = dp(52)
      isChecked = vehicle.brake()
      setOnCheckedChangeListener { _, on -> vehicle.configure(vehicle.gear(), on, vehicle.freshness(), vehicle.position()); render() }
    })
    page.addView(text("Telemetry: ${vehicle.freshness()}", 16f))
    val quality = row()
    listOf("Fresh", "Stale", "Unavailable").forEach { value -> quality.addView(action(value) {
      vehicle.configure(vehicle.gear(), vehicle.brake(), value, vehicle.position()); render()
    }, LinearLayout.LayoutParams(0, -2, 1f)) }
    page.addView(quality)
    val position = text("Demo position: ${vehicle.position()} (simulation units)", 16f)
    page.addView(position)
    page.addView(SeekBar(this).apply {
      max = 100; progress = vehicle.position()
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
          if (fromUser) {
            vehicle.configure(vehicle.gear(), vehicle.brake(), vehicle.freshness(), value)
            position.text = "Demo position: $value (simulation units)"
          }
        }
        override fun onStartTrackingTouch(bar: SeekBar?) {}
        override fun onStopTrackingTouch(bar: SeekBar?) {}
      })
    })
  }

  private fun renderSettings() {
    val bindings=ChildBindings(this)
    val panel=HomePanelSettings(this)
    fun choice(preset:Preset)=PresetChoice(preset.id,preset.name)
    settingsState.value=SettingsUiState(
      presets=store.all().map(::choice),favourites=store.favourites().map(::choice),storageMessage=store.storageMessage(),
      movementEnabled=SeatControl.enabled(this),movementPaused=!BuildConfig.DEMO && !SeatWritePolicy.allowed(),
      homeReady=Settings.canDrawOverlays(this) && panel.observerEnabled(),homeEnabled=panel.enabled,
      panelWidth=PanelPlacement.width(this),panelMessage=panelSetupMessage,panelBusy=panelSetupBusy,
      childEnabled=bindings.enabled,leftPresetId=bindings.preset(ChildSide.LEFT)?.id,rightPresetId=bindings.preset(ChildSide.RIGHT)?.id,
      startupEnabled=Startup.enabled(this),startupGranted=Startup.canWrite(this),startupStatus=store.startup,
      checkOnStart=store.checkUpdatesOnStart,autoUpdates=SilentUpdater.enabled(this),
      autoUpdateStatus=if(SilentUpdater.enabled(this)) SilentUpdater.status(this) else null,updateMessage=updateMessage,updateBusy=updateBusy,
      releaseVersion=release?.versionName,developer=store.developerMode,
      canExport=Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).resolveActivity(packageManager)!=null,
      canImport=Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).resolveActivity(packageManager)!=null,
      demo=BuildConfig.DEMO,version=BuildConfig.VERSION_NAME)
    if(settingsView==null) {
      settingsView=ComposeView(this).apply {
        id=R.id.settings_compose
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { settingsState.value?.let { SeatSettingsScreen(it,::settingsAction) } }
      }
      setContentView(checkNotNull(settingsView))
    }
  }
  private fun settingsAction(action:SettingsAction) {
    runCatching {
      when(action) {
        is SettingsAction.Rename -> { require(action.name.isNotBlank() && action.name.length<=24);store.save(store.all().map { if(it.id==action.id) it.copy(name=action.name) else it });changed() }
        is SettingsAction.Favourite -> { val preset=store.all().first { it.id==action.id };store.setFavourite(action.slot,preset);changed() }
        is SettingsAction.Bind -> { val preset=action.id?.let { id->store.all().first { it.id==id } };ChildBindings(this).assign(action.side,preset);render() }
        is SettingsAction.PanelWidth -> { PanelPlacement.setWidth(this,action.width);render() }
        is SettingsAction.Toggle -> settingsToggleAction(action.setting,action.enabled)
        is SettingsAction.Command -> settingsCommand(action.command)
      }
    }.onFailure { toast(it.message ?: "Could not save setting");render() }
  }
  private fun settingsToggleAction(setting:SettingsToggle,on:Boolean) {
    when(setting) {
      SettingsToggle.DEVELOPER -> { store.developerMode=on;if(!on) { HomePanelSettings(this).parkOnly=true;demoControls=false };render() }
      SettingsToggle.HOME -> {
        check(!on || (Settings.canDrawOverlays(this) && HomePanelSettings(this).observerEnabled())) { "Set up home access first" }
        HomePanelSettings(this).enabled=on;HomeVisibilityService.instance?.refreshVehicle();render()
      }
      SettingsToggle.CHILD -> {
        check(!on || HomePanelSettings(this).observerEnabled()) { "Set up home access first" }
        ChildBindings(this).enabled=on;HomeVisibilityService.instance?.refreshVehicle();render()
      }
      SettingsToggle.STARTUP -> { check(!on || Startup.canWrite(this)) { "Complete home access setup first" };Startup.setEnabled(this,on);render() }
      SettingsToggle.CHECK_UPDATES -> { store.checkUpdatesOnStart=on;render() }
      SettingsToggle.AUTO_UPDATES -> { SilentUpdater.setEnabled(this,on);if(on) Startup.retry(this);render() }
      SettingsToggle.MOVEMENT -> { SeatControl.enable(this,on);HomeVisibilityService.instance?.refreshVehicle();render() }
    }
  }
  private fun settingsCommand(command:SettingsCommand) {
    when(command) {
      SettingsCommand.BACK -> { settings=false;render() }
      SettingsCommand.SETUP_HOME -> setupPanel(true)
      SettingsCommand.RESET_PANEL -> { PanelPlacement.reset(this);render() }
      SettingsCommand.CAR_STARTUP -> openCarStartup()
      SettingsCommand.CHECK_UPDATES -> checkUpdates()
      SettingsCommand.INSTALL -> release?.let { latest->backgroundTask { SilentUpdater.install(this,latest);updateMessage="Update installed" } }
      SettingsCommand.INSTALLER -> release?.let { download(it) }
      SettingsCommand.INSPECT -> inspectSavedPositions()
      SettingsCommand.SETUP_READS -> setupVehicleReadings()
      SettingsCommand.PRIVATE_REPORT -> showReport("Private seat observations",PrivateDiagnostics.report(this))
      SettingsCommand.CLEAR_PRIVATE -> PrivateDiagnostics.clear(this)
      SettingsCommand.SETUP_LOG -> showReport("Setup log",WidgetDiagnostics.report(this))
      SettingsCommand.COPY_LOG -> copyText("Setup diagnostics",WidgetDiagnostics.report(this))
      SettingsCommand.EXPORT -> exportPresets()
      SettingsCommand.IMPORT -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),42)
      SettingsCommand.RESTORE_CONNECTION -> { toast(Startup.reopen(this));render() }
      SettingsCommand.COPY_SETUP -> copyText("Startup permission","adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
      SettingsCommand.DEMO -> { if(BuildConfig.DEMO) { settings=false;demoControls=true;render() } }
      SettingsCommand.MOTION -> { if(BuildConfig.DEMO) startActivity(Intent(this,MotionPreviewActivity::class.java)) }
      SettingsCommand.KEEP_RECOVERY -> { store.acceptRecovery();changed() }
    }
  }
  private fun copyText(label:String,value:String) {
    val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label,value));toast("Copied")
  }

  private var exportContents: String? = null
  private fun exportPresets() { runCatching { saveBackupFile() }.onFailure { toast("File export is unavailable on this device") } }
  private fun saveBackupFile() {
    exportContents = store.exportDocument()
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "seat-presets.json"), 41)
  }
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode != RESULT_OK) { exportContents = null; return }
    val uri = data?.data ?: return
    runCatching {
      if (requestCode == 41) {
        val raw = exportContents ?: store.exportDocument()
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(raw.toByteArray(Charsets.UTF_8)) } ?: error("Cannot open backup file")
        exportContents = null; toast("Presets exported")
      } else if (requestCode == 42) {
        val raw = contentResolver.openInputStream(uri)?.use { input ->
          val output = java.io.ByteArrayOutputStream(); val chunk = ByteArray(4096)
          while (output.size() <= PresetCodec.MAX_BYTES) { val count = input.read(chunk, 0, minOf(chunk.size, PresetCodec.MAX_BYTES + 1 - output.size())); if (count < 0) break; output.write(chunk, 0, count) }
          require(output.size() <= PresetCodec.MAX_BYTES) { "Preset file is too large" }; output.toString("UTF-8")
        } ?: error("Cannot open preset file")
        val doc = PresetCodec.decode(raw)
        AlertDialog.Builder(this).setTitle("Replace saved presets?")
          .setMessage("Import ${doc.presets.size} presets, including ${doc.favourites.joinToString(" and ") { id -> doc.presets.first { it.id == id }.name }} as favourites? Names and positions will replace this app's saved presets. No seat movement.")
          .setNegativeButton("Cancel", null).setPositiveButton("Import") { _, _ -> runCatching { store.importDocument(raw); changed(); toast("Presets imported") }.onFailure { toast("Import failed. Existing presets were kept.") } }.show()
      }
    }.onFailure { WidgetDiagnostics.record(this, "backup result failed request=$requestCode type=${it.javaClass.simpleName}"); toast("Backup failed. Saved presets were kept.") }
  }

  private fun setupVehicleReadings() {
    if(captureBusy) return
    captureBusy=true;val session=captureSession;toast("Setting up vehicle readings…")
    worker.execute {
      val report=runCatching { VehicleReadSetup.enable(this) }.getOrElse {
        SeatDiagnostics.event(this,"read-setup","failed="+SeatDiagnostics.failure(it))
        "Vehicle reading setup could not finish. Keep the setup connection available and retry. See the private report."
      }
      runOnUiThread {
        captureBusy=false
        if(!isDestroyed && !isFinishing && session==captureSession) showReport("Vehicle readings",report)
      }
    }
  }
  private fun inspectSavedPositions() {
    if(captureBusy) return
    captureBusy=true;val session=captureSession;toast("Reading private diagnostics…")
    worker.execute {
      val report=runCatching { SeatDiagnostics.inspect(this) }.getOrElse { "Inspection unavailable: "+SeatDiagnostics.failure(it) }
      runOnUiThread {
        captureBusy=false
        if(!isDestroyed && !isFinishing && session==captureSession) showReport("Private saved positions",report)
      }
    }
  }
  private fun showReport(title:String,contents:String) {
    val view=TextView(this).apply { text=contents;setPadding(dp(20),dp(16),dp(20),dp(16));setTextIsSelectable(true) }
    AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(view) }).setPositiveButton("Close",null).show()
  }
  private fun openCarStartup() {
    runCatching { startActivity(Intent().setComponent(ComponentName("com.byd.appstartmanagement","com.byd.appstartmanagement.StartupAppManageActivity"))) }
      .onFailure { toast("Car startup settings are unavailable here") }
  }
  private fun capture(preset: Preset) {
    if (!BuildConfig.DEMO) { captureLive(preset); return }
    val vehicle = Vehicle.adapter(this)
    if (vehicle.capture() == null) return toast("Seat capture is not available yet")
    RecallPolicy.blockReason(vehicle.snapshot(), android.os.SystemClock.elapsedRealtime())?.let { return toast(it) }
    AlertDialog.Builder(this).setTitle("Save position for ${preset.name}?")
      .setMessage(if (preset.position == null) "Capture the current ${if (BuildConfig.DEMO) "simulated " else ""}position." else "Replace the saved position?")
      .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
        runCatching {
          RecallPolicy.blockReason(vehicle.snapshot(), android.os.SystemClock.elapsedRealtime())?.let { error(it) }
          val current = vehicle.capture() ?: error("Seat capture is not available yet")
          require(current.source == vehicle.source && current.coordinateFormat == vehicle.coordinateFormat) { "Position format is incompatible" }
          store.save(store.all().map { if (it.id == preset.id) it.copy(position = current, capturedAtMs = System.currentTimeMillis()) else it }); changed()
        }.onFailure { toast(it.message ?: "Could not save position") }
      }.show()
  }
  private fun captureLive(preset: Preset) {
    if (captureBusy) return
    AlertDialog.Builder(this).setTitle("Save current position for ${preset.name}?")
      .setMessage(if (preset.position == null) "Park in P. This saves the current driver's seat position." else "Park in P. Replace the saved position with the current one?")
      .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
        captureBusy = true; val session = captureSession
        toast("Saving position…")
        worker.execute {
          val trace="capture-"+java.util.UUID.randomUUID()
          SeatDiagnostics.event(this,trace,"presetId=${preset.id} requestedAtMs=${android.os.SystemClock.elapsedRealtime()}")
          val result = runCatching {
            val bridge = OemReadBridge(this)
            val park=bridge.rawPark()
            SeatDiagnostics.event(this,trace,"rawGearMode=${park.first} rawEPB=${park.second} noCommand=true")
            check(park.first == 1) { "Select P before saving" }
            val a = bridge.seat(); Thread.sleep(200); val b = bridge.seat()
            SeatDiagnostics.event(this,trace,SeatDiagnostics.position("first",a))
            SeatDiagnostics.event(this,trace,SeatDiagnostics.position("second",b))
            check(SeatObservation.repeatable(a, b)) { "Seat is moving. Try again when stopped." }
            check(bridge.rawPark().first == 1 && session == captureSession) { "Save cancelled" }
            // Only persist an observation. No inferred freshness, identity or vehicle command.
            store.save(store.all().map { if (it.id == preset.id) it.copy(position = b, capturedAtMs = System.currentTimeMillis()) else it })
          }.onFailure { SeatDiagnostics.event(this,trace,"saveFailed="+SeatDiagnostics.failure(it)) }
          runOnUiThread {
            captureBusy = false
            if (!isDestroyed && session == captureSession) {
              result.fold({ toast("Position saved for ${preset.name}"); changed() }, { toast(it.message ?: "Could not read the seat position") })
            }
          }
        }
      }.show()
  }
  private fun editPreset(preset: Preset) {
    AlertDialog.Builder(this).setTitle(preset.name).setItems(arrayOf("Save current position…", "Rename", "Use as favourite 1", "Use as favourite 2")) { _, choice ->
      when (choice) {
        0 -> capture(preset)
        1 -> nameDialog("Rename preset", preset.name) { name -> store.save(store.all().map { if (it.id == preset.id) it.copy(name = name) else it }); changed() }
        else -> { runCatching { store.setFavourite(choice - 2, preset); changed() }.onFailure { toast(it.message ?: "Could not save favourite") } }
      }
    }.show()
  }
  private fun nameDialog(title: String, initial: String, save: (String) -> Unit) {
    val input = EditText(this).apply { setText(initial); maxLines = 1; filters = arrayOf(android.text.InputFilter.LengthFilter(24)) }
    val dialog = AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
      val name = input.text.toString().trim()
      if (name.isEmpty()) input.error = "Enter a name" else { runCatching { save(name); dialog.dismiss() }.onFailure { input.error = it.message ?: "Could not save name" } }
    } }; dialog.show()
  }
  private fun changed() { PresetWidget.refresh(this); render() }
  private fun setupPanel(enable: Boolean) {
    if (panelSetupBusy) return
    panelSetupBusy = true; panelSetupMessage = "Preparing home access… Approve the Android prompt if one appears."; render()
    worker.execute {
      val message = runCatching { if (enable) PanelPermissionSetup.enable(this) else PanelPermissionSetup.disable(this) }
        .getOrElse { WidgetDiagnostics.record(this, "panel setup failed ${it.javaClass.simpleName}"); if (store.developerMode) "Setup failed: ${it.javaClass.simpleName}. Keep ADB enabled, approve the debugging prompt, then retry." else "Setup could not finish. Approve the Android prompt if shown, then retry. Developer mode has connection details." }
      runOnUiThread { panelSetupBusy = false; panelSetupMessage = message; if (!isDestroyed) render() }
    }
  }
  private fun checkUpdates() = backgroundTask {
    release = PublicUpdater.latest()
    updateMessage = release?.let { "Update available: ${it.versionName}" } ?: "No newer public release"
  }
  private fun download(latest: PublicRelease) = backgroundTask {
    PublicUpdater.downloadAndVerify(this, latest)
    updateMessage = "APK verified. Complete installation in Android."
    runOnUiThread {
      if (isDestroyed) return@runOnUiThread
      runCatching {
        if (!packageManager.canRequestPackageInstalls()) {
          toast("Allow installs from Seat Presets, then tap Download again")
          startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        } else {
          startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://$packageName.updates/apk"), "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
      }.onFailure { updateMessage = "Android installer unavailable: ${it.javaClass.simpleName}"; render() }
    }
  }

  private fun backgroundTask(task: () -> Unit) {
    if (updateBusy) return
    updateBusy = true; updateMessage = "Checking public release…"; render()
    worker.execute {
      try { task() } catch (e: Exception) { updateMessage = e.message ?: "Update failed" }
      finally { runOnUiThread { if (!isDestroyed) { updateBusy = false; render() } } }
    }
  }
  private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  private fun text(value: String, size: Float, color: Int = textColor) = TextView(this).apply {
    text = value; setTextColor(color); textSize = size
    if (size >= 22f) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setPadding(0, dp(8), 0, dp(8))
  }
  private fun action(label: String, click: () -> Unit) = Button(this).apply {
    text = label; isAllCaps = false; textSize = 17f; minHeight = dp(if(settings) 76 else 56)
    layoutParams=LinearLayout.LayoutParams(-1,-2).apply { if(settings) { topMargin=dp(8);bottomMargin=dp(4) } }
    setTextColor(textColor); background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x224A90D9), rounded(if (settings) Color.rgb(38, 44, 51) else getColor(R.color.matte_raised), 14), null)
    stateListAnimator = null
    setPadding(dp(20), dp(12), dp(20), dp(12))
    setOnClickListener { runCatching(click).onFailure { toast(it.message ?: "Action failed") } }
  }
  private fun primaryAction(label: String, click: () -> Unit) = action(label, click).apply {
    textSize = 21f
    setTextColor(getColor(R.color.matte_ink))
    background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33101820), rounded(accentColor, 14), null)
  }
  private fun rounded(color: Int, radius: Int, outline: Boolean = false) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radius).toFloat()
    if (outline) setStroke(dp(1), getColor(R.color.matte_border))
  }
  private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

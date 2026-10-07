package dev.lua.seatpreset

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
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

class MainActivity : Activity() {
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
    handlePanelRequest(intent)
    runCatching { if (StorageAccess.unlocked(this) && store.checkUpdatesOnStart) checkUpdates() }
  }
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent); setIntent(intent); handlePanelRequest(intent)
  }
  private fun handlePanelRequest(request: Intent) {
    val id=request.getStringExtra("test-preset-id") ?: return
    request.removeExtra("test-preset-id")
    runCatching { store.all().firstOrNull { it.id == id }?.let { openMovement(it) } }
      .onFailure { toast("Saved position is unavailable") }
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
    setContentView(android.widget.TextView(this).apply {
      text = getString(R.string.storage_not_ready); setPadding(32, 32, 32, 32)
      setOnClickListener { Startup.retry(this@MainActivity); safeRender() }
    })
  } }
  private fun renderReady() {
    StorageAccess.requireUnlocked(this)
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
    if (settings) renderSettings() else if (demoControls && BuildConfig.DEMO) renderDemo() else renderPresets()
    val frame = FrameLayout(this).apply {
      addView(page, FrameLayout.LayoutParams(dp(minOf(resources.configuration.screenWidthDp, 1280)), -2, android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL))
    }
    setContentView(ScrollView(this).apply { setBackgroundColor(backgroundColor); addView(frame) })
  }

  private fun renderPresets() {
    val vehicle = Vehicle.adapter(this)
    val block = if (BuildConfig.DEMO) RecallPolicy.blockReason(vehicle.snapshot(), android.os.SystemClock.elapsedRealtime()) else "Hold a card to save your position. Tap to test a saved position."
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
              else openMovement(preset)
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
        footer.addView(text(if (saved) { if (BuildConfig.DEMO) "Use preset  →" else "Test position  →" } else "Hold to save", 18f, accentColor))
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
    val root = page
    val wide = resources.configuration.screenWidthDp >= 1_000
    var columns = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
    fun group(title: String, body: () -> Unit) {
      val section = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(20))
        background = rounded(panelColor, 24)
      }
      page = section
      page.addView(text(title, 22f))
      try { body() } finally { page = root }
      columns.addView(section, (if (wide) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2)).apply {
        marginEnd = dp(if (wide) 8 else 0); topMargin = dp(16); bottomMargin = dp(8)
      })
    }
    group("Drivers") {
      store.favourites().forEachIndexed { slot, preset ->
        val driverHeading = row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        driverHeading.addView(text(preset.name, 24f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        val controls = row()
        driverHeading.addView(action("Rename") { nameDialog("Driver name", preset.name) { name ->
          store.save(store.all().map { if (it.id == preset.id) it.copy(name = name) else it }); changed()
        } }.apply { contentDescription = "Rename driver ${slot + 1}" }, LinearLayout.LayoutParams(-2, dp(56)))
        page.addView(driverHeading)
        controls.addView(action("Choose preset") {
          val all = store.all()
          AlertDialog.Builder(this).setTitle("Choose favourite ${slot + 1}").setItems(all.map { it.name }.toTypedArray()) { _, i ->
            runCatching { store.setFavourite(slot, all[i]); changed() }.onFailure { toast(it.message ?: "Could not save favourite") }
          }.show()
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(if (resources.configuration.screenWidthDp < 700) 0 else 8); bottomMargin = dp(8) })
        page.addView(controls)
      }
      store.storageMessage()?.let { message ->
        page.addView(text(message, 15f, getColor(R.color.matte_caution)))
        if (message.startsWith("Recovered")) page.addView(action("Confirm recovered presets") {
          AlertDialog.Builder(this).setTitle("Keep recovered presets?").setMessage("Check the recovered driver names and saved positions before keeping this previous version. This does not move the seat.")
            .setNegativeButton("Cancel", null).setPositiveButton("Keep presets") { _, _ -> runCatching { store.acceptRecovery(); changed() }.onFailure { toast("Could not recover presets") } }.show()
        })
      }
    }
    group("Home panel") {
      val panelSettings = HomePanelSettings(this)
      page.addView(text("Only on home while P is reported. Drag to move.", 16f, mutedColor))
      if (!Settings.canDrawOverlays(this) || !panelSettings.observerEnabled()) page.addView(text("Home access needs setup.", 15f, mutedColor))
      page.addView(action("Set up home panel") {
        AlertDialog.Builder(this).setTitle("Enable home panel access?")
          .setMessage("Allow the panel to appear on home. Approve the Android debugging prompt if shown. No seat movement.")
          .setNegativeButton("Cancel", null).setPositiveButton("Set up") { _, _ -> setupPanel(true) }.show()
      }.apply { isEnabled = !panelSetupBusy })
      if (panelSetupMessage.isNotBlank()) page.addView(text(panelSetupMessage, 15f, mutedColor))
      page.addView(Switch(this).apply {
        text = "Show panel on home in P"; setTextColor(textColor); minHeight = dp(52); isChecked = panelSettings.enabled
        setOnCheckedChangeListener { _, on ->
          if (on && (!Settings.canDrawOverlays(this@MainActivity) || !panelSettings.observerEnabled())) { toast("Set up home panel first"); isChecked = false }
          else runCatching { panelSettings.enabled = on; HomeVisibilityService.instance?.refreshVehicle() }.onFailure { toast("Could not save panel choice"); render() }
        }
      })
      val sizeLabel = text("Panel size: ${PanelPlacement.width(this)}", 16f, mutedColor); page.addView(sizeLabel)
      page.addView(SeekBar(this).apply {
        max = 200; progress = PanelPlacement.width(this@MainActivity) - 320
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
          override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) { if (user) { PanelPlacement.setWidth(this@MainActivity, value + 320); sizeLabel.text = "Panel size: ${value + 320}" } }
          override fun onStartTrackingTouch(bar: SeekBar?) {}
          override fun onStopTrackingTouch(bar: SeekBar?) { render() }
        })
      })
      val previewWidth = minOf(PanelPlacement.width(this), if (wide) resources.configuration.screenWidthDp / 2 - 96 else resources.configuration.screenWidthDp - 104)
      page.addView(FloatingPanelView.create(this, { toast("The close button turns off the home panel") }, { toast("Appearance preview only") }),
        LinearLayout.LayoutParams(dp(previewWidth), dp(previewWidth / 2)).apply { topMargin = dp(8); bottomMargin = dp(16) })
      val placement = row().apply { if (resources.configuration.screenWidthDp < 600) orientation = LinearLayout.VERTICAL }
      placement.addView(action("Reset placement") { PanelPlacement.reset(this); render() }, LinearLayout.LayoutParams(if (resources.configuration.screenWidthDp < 600) -1 else -2, -2).apply { bottomMargin = dp(8) })
      page.addView(placement)
    }
    root.addView(columns)
    columns = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
    group("Child-lock shortcuts") {
      ChildSide.values().forEach { side ->
        val bindings = ChildBindings(this)
        page.addView(action("${if (side == ChildSide.LEFT) "Left" else "Right"} double tap: ${bindings.preset(side)?.name ?: "Off"}") {
          val all = store.all()
          AlertDialog.Builder(this).setTitle("${side.name.lowercase().replaceFirstChar { it.uppercase() }} double tap")
            .setItems((listOf("Off") + all.map { it.name }).toTypedArray()) { _, i -> runCatching { bindings.assign(side, if (i == 0) null else all[i-1]); render() }.onFailure { toast("Could not save shortcut") } }.show()
        })
      }
      page.addView(text("Test in P. Feedback only until seat control is verified.", 15f, mutedColor))
      page.addView(action("Test shortcuts") { startActivity(Intent(this, ChildTrialActivity::class.java)) })
    }
    root.addView(columns)
    columns = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
    group("Startup") {
    page.addView(action("Open car startup settings") {
      runCatching { startActivity(Intent().setComponent(ComponentName("com.byd.appstartmanagement", "com.byd.appstartmanagement.StartupAppManageActivity"))) }
        .onFailure { toast("Car startup settings are not available on this device") }
    })
    page.addView(Switch(this).apply {
      text = "Restore setup connection after restart"; setTextColor(textColor); minHeight = dp(52)
      isChecked = Startup.enabled(this@MainActivity)
      setOnCheckedChangeListener { _, on ->
        if (on && !Startup.canWrite(this@MainActivity)) { toast("Complete home access setup first"); render() }
        else { runCatching { Startup.setEnabled(this@MainActivity, on) }.onFailure { toast("Could not save startup choice") }; render() }
      }
    })
    }
    group("Updates") {
    page.addView(text("${BuildConfig.VERSION_NAME} · ${BuildConfig.FLAVOR}", 16f, mutedColor))
    page.addView(Switch(this).apply {
      text = "Check on start"; setTextColor(textColor); minHeight = dp(52); isChecked = store.checkUpdatesOnStart
      setOnCheckedChangeListener { _, on -> store.checkUpdatesOnStart = on }
    })
    page.addView(Switch(this).apply {
      text = "Automatic updates"; setTextColor(textColor); minHeight = dp(52)
      isChecked = SilentUpdater.enabled(this@MainActivity)
      setOnCheckedChangeListener { _, on ->
        if (!on) { runCatching { SilentUpdater.setEnabled(this@MainActivity, false) }.onFailure { toast("Could not save update choice") }; render() }
        else android.app.AlertDialog.Builder(this@MainActivity).setTitle("Automatic updates")
          .setMessage("Download verified releases and install them when this app starts. Requires completed home access setup. Driver names and presets are kept. No seat movement is requested.")
          .setNegativeButton("Cancel") { _, _ -> render() }
          .setPositiveButton("Enable") { _, _ -> runCatching { SilentUpdater.setEnabled(this@MainActivity, true); Startup.retry(this@MainActivity) }.onFailure { toast("Could not save update choice") }; render() }.show()
      }
    })
    if (SilentUpdater.enabled(this)) page.addView(text(SilentUpdater.status(this), 16f, mutedColor))
    page.addView(text(updateMessage, 16f))
    page.addView(action("Check updates") { checkUpdates() }.apply { isEnabled = !updateBusy })
    release?.let { latest ->
      page.addView(primaryAction("Install ${latest.versionName}") { backgroundTask {
        SilentUpdater.install(this, latest)
        updateMessage = "Update installed"
      } }.apply { isEnabled = !updateBusy })
      page.addView(action("Use Android installer instead") { download(latest) }.apply { isEnabled = !updateBusy })
    }
    }
    root.addView(columns)
    page.addView(Switch(this).apply {
      text = "Developer mode"; setTextColor(textColor); minHeight = dp(52); isChecked = store.developerMode
      setOnCheckedChangeListener { _, on -> store.developerMode = on; if (!on) { HomePanelSettings(this@MainActivity).parkOnly = true; demoControls = false }; render() }
    })
    if (store.developerMode) renderDeveloperSettings()
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

  private fun renderDeveloperSettings() {
    page.addView(action("Export to file") { exportPresets() })
    page.addView(action("Import from file") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 42) })
    if (!BuildConfig.DEMO) page.addView(action("Supervised seat movement trial") { startActivity(Intent(this, SeatMovementTrialActivity::class.java)) })
    page.addView(action("Child-lock input trial · screen only") { startActivity(Intent(this, ChildTrialActivity::class.java)) })
    page.addView(text("Developer tools", 22f, accentColor))
    page.addView(action("View private trial report") {
      val report = TextView(this).apply { text = PrivateDiagnostics.report(this@MainActivity); setPadding(dp(16), dp(16), dp(16), dp(16)); setTextIsSelectable(true) }
      AlertDialog.Builder(this).setTitle("Private seat/input observations").setView(ScrollView(this).apply { addView(report) }).setPositiveButton("Close", null).show()
    })
    page.addView(action("Clear private trial report") {
      AlertDialog.Builder(this).setTitle("Clear private observations?").setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ -> PrivateDiagnostics.clear(this) }.show()
    })
    val panelSettings = HomePanelSettings(this)
    page.addView(Switch(this).apply {
      text = "Ignore gear for layout testing"; setTextColor(textColor); minHeight = dp(52); isChecked = !panelSettings.parkOnly
      setOnCheckedChangeListener { _, on -> panelSettings.parkOnly = !on; HomeVisibilityService.instance?.hide() }
    })
    if (BuildConfig.DEMO) {
      page.addView(action("Demo controls") { settings = false; demoControls = true; render() })
      page.addView(action("Preview panel motion") { startActivity(Intent(this, MotionPreviewActivity::class.java)) })
    }
    val permissionIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
    if (!Settings.canDrawOverlays(this) && permissionIntent.resolveActivity(packageManager) == null) {
      val command = "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow"
      page.addView(text("This system has no overlay permission screen. Run once from your trusted ADB connection:\n$command\nTo undo, use default instead of allow.", 15f, mutedColor))
      page.addView(action("Copy permission command") {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Floating panel permission", command))
        toast("Permission command copied")
      })
    }
    page.addView(action(if (Settings.canDrawOverlays(this)) "Open home panel" else if (permissionIntent.resolveActivity(packageManager) == null) "Check overlay permission" else "Allow floating panel") {
      if (!Settings.canDrawOverlays(this)) {
        if (permissionIntent.resolveActivity(packageManager) == null) {
          toast("Run the permission command, then reopen this app")
          render()
        } else runCatching { startActivity(permissionIntent) }
          .onFailure { toast("Overlay permission screen is unavailable") }
      } else if (panelSettings.enabled) {
        toast("Return to home in P to see the panel")
      } else {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (home.resolveActivity(packageManager) == null) toast("Home screen is unavailable")
        else {
          HomePanelSettings(this).enabled = true
          runCatching { startActivity(home) }.onFailure { toast("Could not open home") }
        }
      }
    })
    page.addView(text("Standard Android widget", 22f))
    page.addView(text("Two favourites, directly from home.", 16f, mutedColor))
    val preview = PresetWidget.views(this, 0).apply(this, page)
    store.favourites().forEachIndexed { slot, preset ->
      preview.findViewById<View>(if (slot == 0) R.id.favourite_one_target else R.id.favourite_two_target).setOnClickListener {
        toast(Vehicle.recall(this, preset).message); render()
      }
    }
    page.addView(preview, LinearLayout.LayoutParams(dp(minOf(420, resources.configuration.screenWidthDp - 64)), dp(220)).apply { topMargin = dp(8); bottomMargin = dp(16) })
    page.addView(action("Add home widget") { pinWidget() })
    page.addView(text("Widget diagnostics", 22f))
    page.addView(text(WidgetDiagnostics.report(this), 14f, mutedColor))
    page.addView(action("Refresh widget diagnostics") { PresetWidget.refresh(this); render() })
    page.addView(action("Copy widget diagnostics") {
      val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Seat Presets widget diagnostics", WidgetDiagnostics.report(this)))
      toast("Widget diagnostics copied")
    })
    page.addView(action("Replay child-lock traces") { startActivity(Intent(this, ChildReplayActivity::class.java)) })
    page.addView(text("NFC identity and cabin buttons", 22f, accentColor))
    page.addView(text("Child-lock state reads are verified in the lab. Gesture reliability needs parked trials. NFC identity tests remain pending.", 16f, mutedColor))
    page.addView(text("Startup", 22f, accentColor))
    page.addView(text(store.startup, 16f, mutedColor))
    page.addView(action("Open BYD autostart manager") {
      runCatching { startActivity(Intent().setComponent(ComponentName("com.byd.appstartmanagement", "com.byd.appstartmanagement.StartupAppManageActivity"))) }
        .onFailure { toast("BYD autostart manager is not installed here") }
    })
    page.addView(Switch(this).apply {
      text = "Re-enable ADB settings on startup"; setTextColor(textColor); minHeight = dp(52)
      isChecked = Startup.enabled(this@MainActivity)
      setOnCheckedChangeListener { _, on -> runCatching { Startup.setEnabled(this@MainActivity, on) }.onFailure { toast("Could not save startup choice") }; if (on) Startup.reopen(this@MainActivity); render() }
    })
    page.addView(text(if (Startup.canWrite(this)) "Startup permission granted" else "One-time setup from a trusted ADB connection:\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS", 15f, mutedColor))
    page.addView(action("Re-enable ADB settings now") { toast(Startup.reopen(this)); render() })
  }

  private fun openMovement(preset: Preset) {
    if (preset.position == null) { toast("Hold the card to save a position first"); return }
    startActivity(Intent(this, SeatMovementTrialActivity::class.java).putExtra("preset-id", preset.id))
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
          val result = runCatching {
            val bridge = OemReadBridge(this)
            check(bridge.rawPark().first == 1) { "Select P before saving" }
            val a = bridge.seat(); Thread.sleep(200); val b = bridge.seat()
            check(SeatObservation.repeatable(a, b)) { "Seat is moving. Try again when stopped." }
            check(bridge.rawPark().first == 1 && session == captureSession) { "Save cancelled" }
            // Only persist an observation. No inferred freshness, identity or vehicle command.
            store.save(store.all().map { if (it.id == preset.id) it.copy(position = b, capturedAtMs = System.currentTimeMillis()) else it })
          }
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
  private fun pinWidget() {
    val manager = AppWidgetManager.getInstance(this)
    WidgetDiagnostics.record(this, "pin requested supported=${manager.isRequestPinAppWidgetSupported}")
    if (!manager.isRequestPinAppWidgetSupported) return toast("Add Seat Presets from your launcher's widget picker")
    val callback = android.app.PendingIntent.getBroadcast(this, 701,
      Intent(this, PresetWidget::class.java).setAction("$packageName.PIN_CONFIRMED"),
      android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
    runCatching {
      val accepted = manager.requestPinAppWidget(ComponentName(this, PresetWidget::class.java), null, callback)
      WidgetDiagnostics.record(this, "pin request returned=$accepted")
      if (!accepted) toast("Launcher did not accept the widget request")
    }.onFailure {
      WidgetDiagnostics.record(this, "pin request failed ${it.javaClass.simpleName}")
      toast("Could not request the widget")
    }
  }
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
    text = label; isAllCaps = false; textSize = 17f; minHeight = dp(56)
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

package dev.lua.seatpreset

import android.content.Context
import android.os.SystemClock

/** Seat data stays in the credential-protected private report. No public logger or automatic export path. */
internal object SeatDiagnostics {
  fun event(context: Context, trace: String, detail: String) {
    PrivateDiagnostics.record(context, "trace=$trace " + detail.replace('\n', ' ').replace('\r', ' ').take(4096))
  }
  fun position(stage: String, value: Position?): String = if (value == null) "$stage unavailable" else
    "$stage source=${value.source} format=${value.coordinateFormat} raw=${value.coordinates.toSortedMap()} units=unverified"
  fun failure(error: Throwable): String = generateSequence(error) { it.cause }.take(6)
    .joinToString(" <- ") { "${it.javaClass.simpleName}:${it.message.orEmpty().replace('\n', ' ').replace('\r', ' ').take(160)}" }
  fun inspect(context: Context): String {
    StorageAccess.requireUnlocked(context)
    val trace = "inspect-" + java.util.UUID.randomUUID()
    val lines = mutableListOf("Read-only inspection. No seat command. Getter times do not prove OEM-cache freshness.")
    fun add(line: String) { lines += line; event(context, trace, line) }
    add("version=${BuildConfig.VERSION_NAME} sampledAtMs=${SystemClock.elapsedRealtime()} writesAllowed=${SeatWritePolicy.allowed()}")
    val store = PresetStore(context)
    store.storageMessage()?.let { add("storage=$it");return lines.joinToString("\n\n") }
    store.all().forEach { add("presetId=${it.id} capturedAtWallMs=${it.capturedAtMs} " + position("saved", it.position)) }
    (VehicleReadPermissions.required + "android.permission.BYDAUTO_SETTING_SET").forEach { permission ->
      add("permission=${permission.substringAfter("BYDAUTO_")} granted=${context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED}")
    }
    runCatching {
      val bridge = OemReadBridge(context)
      val park = bridge.rawPark()
      add("rawGearMode=${park.first} rawEPB=${park.second} sampledAtMs=${SystemClock.elapsedRealtime()}")
      val current = bridge.seat()
      add(position("observed", current))
      store.all().filter { it.position != null }.forEach { preset ->
        runCatching { ManualSeatRecall.plan(current, checkNotNull(preset.position)) }
          .fold({ add("presetId=${preset.id} candidatePlan=$it noCommand=true") },
            { add("presetId=${preset.id} planRejected=${failure(it)}") })
      }
    }.onFailure { add("readFailed=${failure(it)}") }
    runCatching { OemReadBridge(context).absoluteSeatCandidates() }
      .fold({ add("bodyworkRawCandidates=$it units=unverified ready=unknown noPresetChanged=true noCommand=true") },
        { add("bodyworkCandidatesUnavailable=${failure(it)}") })
    return lines.joinToString("\n\n")
  }
}

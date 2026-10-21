package dev.lua.seatpreset

/** Read-only A/B/A evidence. Completing this sequence never grants movement eligibility. */
internal class NativeSeatObservationTrial {
  data class Sample(val atMs: Long, val values: Map<String, Double?>)
  private val samples = mutableListOf<Sample>()
  private var axis: String? = null
  fun stage() = listOf("A: starting position", "B: after a small native adjustment", "A: returned with native controls")[samples.size.coerceAtMost(2)]
  fun complete() = samples.size == 3
  fun reset() { samples.clear(); axis = null }
  fun add(selectedAxis: String, sample: Sample): String {
    require(selectedAxis in SeatObservation.axes) { "Select a known axis" }
    require(sample.atMs >= 0 && sample.values.isNotEmpty() && sample.values.values.filterNotNull().all { it.isFinite() }) { "Invalid observations" }
    require(samples.isEmpty() || (axis == selectedAxis && sample.atMs >= samples.last().atMs && sample.atMs - samples.first().atMs <= 600_000)) { "Trial expired or axis changed. Start a new trial." }
    check(samples.size < 3) { "Trial complete. Start a new trial." }
    if (samples.isNotEmpty()) require(sample.values.keys == samples.first().values.keys) { "Reading routes changed. Start a new trial." }
    axis = selectedAxis
    samples += sample.copy(values = sample.values.toMap())
    if (samples.size < 3) return "Recorded ${samples.size}/3. Next: ${stage()}. No command sent."
    val (a, b, c) = samples
    return buildString {
      appendLine("Native $selectedAxis A/B/A observation complete. No command sent; no writer validated.")
      a.values.keys.sorted().forEach { route ->
        val x = a.values[route]; val y = b.values[route]; val z = c.values[route]
        val verdict = when {
          x == null || y == null || z == null -> "unavailable in at least one phase"
          x == y && y == z -> "flat; cached values or no reported change remain possible"
          x != y && x == z -> "changed and returned; units and command meaning still unverified"
          else -> "changed without exact return; compare native position and read timing"
        }
        appendLine("$route A=$x B=$y A2=$z: $verdict")
      }
      append("Compare all routes for coupled axes. Zero is raw data, not a safe movement target. Getter sample times do not establish ECU-cache freshness.")
    }
  }
}

package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class RecallControllerTest {
  private val position = Position(mapOf("axis" to 40.0), "test", "test-v1")
  private val preset = Preset("one", "Driver one", position)
  private class FakeVehicle : VehicleAdapter {
    override val source = "test"
    override val coordinateFormat = "test-v1"
    override var recallValidated = true
    var state = VehicleSnapshot(Gear.P, true, null, 1_000)
    var writes = 0
    var verified = true
    override fun snapshot() = state
    override fun capture(): Position? = null
    override fun apply(position: Position): Boolean { writes++; return verified }
  }
  @Test fun freshParkedRequestDispatchesOnce() {
    val vehicle = FakeVehicle()
    assertTrue(RecallController(vehicle) { 1_500 }.recall(preset).allowed)
    assertEquals(1, vehicle.writes)
  }
  @Test fun everyOtherGearBlocksEvenWhenSpeedIsZero() {
    listOf(Gear.R, Gear.N, Gear.D, null).forEach { gear ->
      val vehicle = FakeVehicle().apply { state = state.copy(gear = gear, speedKph = 0.0) }
      assertFalse(RecallController(vehicle) { 1_500 }.recall(preset).allowed)
      assertEquals(0, vehicle.writes)
    }
  }
  @Test fun missingReleasedOrStaleParkingEvidenceCannotWrite() {
    val states = listOf(
      VehicleSnapshot(Gear.P, null, null, 1_000), VehicleSnapshot(Gear.P, false, null, 1_000),
      VehicleSnapshot(Gear.P, true, null, null), VehicleSnapshot(Gear.P, true, null, -1_001),
      VehicleSnapshot(Gear.P, true, null, 1_501),
    )
    states.forEach { state ->
      val vehicle = FakeVehicle().apply { this.state = state }
      assertFalse(RecallController(vehicle) { 1_500 }.recall(preset).allowed)
      assertEquals(0, vehicle.writes)
    }
  }
  @Test fun speedIsOnlyAVeto() {
    listOf(1.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { speed ->
      val vehicle = FakeVehicle().apply { state = state.copy(speedKph = speed) }
      assertFalse(RecallController(vehicle) { 1_500 }.recall(preset).allowed)
      assertEquals(0, vehicle.writes)
    }
  }
  @Test fun parkedSnapshotCannotOverrideUnvalidatedAdapter() {
    val vehicle = FakeVehicle().apply { recallValidated = false }
    assertFalse(RecallController(vehicle) { 1_500 }.recall(preset).allowed)
    assertEquals(0, vehicle.writes)
  }
  @Test fun incompatibleOrMissingGeometryCannotWrite() {
    listOf(null, position.copy(source = "other-car"), position.copy(coordinateFormat = "other-format"), position.copy(coordinates = emptyMap()), position.copy(coordinates = mapOf("axis" to Double.NaN))).forEach { geometry ->
      val vehicle = FakeVehicle()
      assertFalse(RecallController(vehicle) { 1_500 }.recall(preset.copy(position = geometry)).allowed)
      assertEquals(0, vehicle.writes)
    }
  }
  @Test fun rejectedRequestDoesNotReplayWhenStateChanges() {
    val vehicle = FakeVehicle().apply { state = state.copy(gear = Gear.D) }
    val controller = RecallController(vehicle) { 1_500 }
    assertFalse(controller.recall(preset).allowed)
    vehicle.state = vehicle.state.copy(gear = Gear.P)
    assertEquals(0, vehicle.writes)
    assertTrue(controller.recall(preset).allowed)
    assertEquals(1, vehicle.writes)
  }
  @Test fun aWriteWithoutVerificationIsNotReportedAsSuccess() {
    val vehicle = FakeVehicle().apply { verified = false }
    assertFalse(RecallController(vehicle) { 1_500 }.recall(preset).allowed)
    assertEquals(1, vehicle.writes)
  }
}

package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class StartupSafeguardsTest {
  @Test fun `failed preference commit cannot enable recovery from its memory copy`() {
    val choice = RecoveryChoice(); var memory: Boolean? = false
    assertTrue(runCatching { choice.save(true) { memory = it; false } }.isFailure)
    assertNull(choice.read { memory })
    choice.retry { memory = it; true }
    assertEquals(true, choice.read { memory })
    assertTrue(runCatching { choice.save(false) { memory = it; false } }.isFailure)
    assertNull(choice.read { memory })
    choice.retry { memory = it; true }
    assertEquals(false, choice.read { memory })
  }
  @Test fun `locked storage never evaluates personal preference factory`() {
    var touched = false
    assertTrue(runCatching { CredentialAccess.read(false) { touched = true } }.isFailure)
    assertFalse(touched)
  }
  @Test fun `unknown disabled open port denied and unreadable settings fail closed`() {
    for (armed in listOf(null, false, true)) for (open in listOf(null, false, true))
      for (permission in listOf(false, true)) for (adb in listOf(null, "", "0", "1"))
        for (wifi in listOf(null, "", "0", "1")) {
          assertEquals(armed == true && open == false && permission && (adb == "0" || wifi == "0"),
            StartupPolicy.needsReassert(armed, open, permission, adb, wifi))
        }
  }
  @Test fun `second settings clear after unlock remains recoverable`() {
    var adb = "0"; var writes = 0
    fun tick(open: Boolean) {
      if (StartupPolicy.needsReassert(true, open, true, adb, "1")) { adb = "1"; writes++ }
    }
    tick(false); tick(false); tick(true)
    adb = "0"; tick(true); assertEquals(1, writes)
    tick(false); assertEquals(2, writes)
  }
  @Test fun `migration waits for unlock and preserves existing opt out`() {
    assertNull(StartupPolicy.migrate(false, { null }, { error("CE read while locked") }, { error("write while locked") }))
    assertEquals(false, StartupPolicy.migrate(true, { false }, { true }, { error("overwrite opt-out") }))
  }
  @Test fun `only explicit legacy selection migrates and failed commit is retried`() {
    var saved: Boolean? = null; var commits = 0
    assertTrue(runCatching { StartupPolicy.migrate(true, { saved }, { true }, { commits++; false }) }.isFailure)
    assertNull(saved)
    assertEquals(true, StartupPolicy.migrate(true, { saved }, { true }, { commits++; saved = it; true }))
    assertEquals(2, commits)
    assertEquals(false, StartupPolicy.migrate(true, { null }, { null }, { assertFalse(it); true }))
  }
  @Test fun `unreadable policy cannot be mistaken for absent policy`() {
    assertTrue(runCatching { StartupPolicy.migrate(true, { error("unreadable") }, { error("legacy") }, { error("save") }) }.isFailure)
  }
  @Test fun `late authentication retries failed reads without blocking later jobs`() {
    val reads = DeferredReadWork(); var attempts = 0; var other = 0
    reads.offer("first") { attempts++; check(attempts > 1) }
    reads.offer("second") { other++ }
    reads.retry(false, true); reads.retry(true, null); reads.retry(true, false)
    assertEquals(0, attempts)
    reads.retry(true, true); assertEquals(1, attempts); assertEquals(1, other)
    reads.retry(true, true); reads.retry(true, true)
    assertEquals(2, attempts); assertEquals(1, other)
  }
  @Test fun `callback bursts coalesce and named reads do not form a backlog`() {
    val gate = RetryGate(); assertTrue(gate.request())
    repeat(1000) { assertFalse(gate.request()) }
    assertTrue(gate.finish()); assertFalse(gate.finish()); assertTrue(gate.request()); assertFalse(gate.finish())
    val reads = DeferredReadWork(1); var count = 0
    repeat(1000) { reads.offer("same") { count++ } }
    reads.retry(true, true); assertEquals(1, count)
  }
  @Test fun `late samples retain separate monotonic clocks and never establish identity`() {
    val times = StartupTimes(10)
    times.storageReady(200); times.storageReady(300); times.sampled(900)
    assertEquals(10L, times.processStartedAtMs); assertEquals(200L, times.storageReadyAtMs)
    assertEquals(900L, times.sampledAtMs); assertFalse(times.freshUnlockIdentity)
  }
}

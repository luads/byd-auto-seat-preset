package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class KeyStorageTest {
  @Test fun `locked storage never inspected read or generated`() {
    var touched = false
    assertTrue(runCatching { KeyStorage.load(false, { touched = true; false to false },
      { touched = true }, { touched = true }) }.isFailure)
    assertFalse(touched)
  }
  @Test fun `existing unreadable key is never replaced`() {
    var generated = false
    val failure = java.io.IOException("locked or corrupt")
    assertSame(failure, runCatching { KeyStorage.load(true, { true to true },
      { throw failure }, { generated = true }) }.exceptionOrNull())
    assertFalse(generated)
  }
  @Test fun `either partial pair refuses both read and generation`() {
    for (pair in listOf(true to false, false to true)) {
      var touched = false
      assertTrue(runCatching { KeyStorage.load(true, { pair }, { touched = true },
        { touched = true }) }.isFailure)
      assertFalse(touched)
    }
  }
  @Test fun `first unlocked use generates once before read existing key only reads`() {
    val calls = mutableListOf<String>()
    assertEquals("key", KeyStorage.load(true, { false to false },
      { calls += "read"; "key" }, { calls += "generate" }))
    assertEquals(listOf("generate", "read"), calls)
    calls.clear()
    KeyStorage.load(true, { true to true }, { calls += "read" }, { calls += "generate" })
    assertEquals(listOf("read"), calls)
  }
}

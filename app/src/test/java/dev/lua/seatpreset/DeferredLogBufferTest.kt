package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class DeferredLogBufferTest {
  private val unavailable: (String) -> Unit = { throw java.io.IOException("locked") }
  @Test fun `failed writes retain original timestamps and order until successful flush`() {
    val buffer = DeferredLogBuffer()
    assertFalse(buffer.append("01:00 b+1 early", unavailable))
    assertFalse(buffer.append("01:01 b+2 later", unavailable))
    var output = ""
    assertTrue(buffer.flush { output += it })
    assertEquals("01:00 b+1 early\n01:01 b+2 later\n", output)
    buffer.flush { error("duplicate write") }
  }
  @Test fun `limits drop oldest lines and report loss`() {
    val buffer = DeferredLogBuffer(maxLines = 2, maxChars = 8)
    listOf("1111", "2222", "3333").forEach { buffer.append(it, unavailable) }
    var output = ""
    buffer.flush { output = it }
    assertEquals("log-buffer: 1 early line(s) dropped at buffer limit\n2222\n3333\n", output)
  }
  @Test fun `oversized line cannot grow queue`() {
    val buffer = DeferredLogBuffer(maxChars = 4)
    buffer.append("secret too long", unavailable)
    var output = ""
    buffer.flush { output = it }
    assertEquals("log-buffer: 1 early line(s) dropped at buffer limit\n", output)
  }
  @Test fun `private queue stays separate and clear discards pending lines`() {
    val public = DeferredLogBuffer()
    val private = DeferredLogBuffer()
    public.append("public", unavailable)
    private.append("phone identity", unavailable)
    var output = ""
    public.flush { output = it }
    assertEquals("public\n", output)
    private.clear()
    private.flush { error("private line reappeared") }
  }
}

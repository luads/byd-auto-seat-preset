package dev.lua.seatpreset

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DiagnosticPreparationTest {
  @Test fun busyGateStaysClosedAcrossOwnerRecreationUntilDeliveryCompletes() {
    val queued = mutableListOf<Runnable>()
    val preparation = DiagnosticPreparation<String>(java.util.concurrent.Executor { queued += it })
    var delivery: (() -> Unit)? = null
    var result: String? = null
    assertTrue(preparation.submit({ "snapshot" }, { delivery = it }, { result = it.getOrThrow() }))
    assertFalse(preparation.submit({ "second owner" }, { it() }, {}))
    queued.removeAt(0).run()
    assertNull(result)
    assertFalse(preparation.submit({ "new Activity" }, { it() }, {}))
    delivery!!()
    assertEquals("snapshot", result)
    assertTrue(preparation.submit({ "retry" }, { it() }, {}))
  }
  @Test fun exportExecutorCompletesWhileAnUnrelatedOemWorkerIsBlocked() {
    val oem = Executors.newSingleThreadExecutor()
    val exporter = Executors.newSingleThreadExecutor()
    val blocked = CountDownLatch(1)
    val release = CountDownLatch(1)
    val delivered = CountDownLatch(1)
    try {
      oem.execute { blocked.countDown(); release.await() }
      assertTrue(blocked.await(2, TimeUnit.SECONDS))
      val preparation = DiagnosticPreparation<String>(exporter)
      assertTrue(preparation.submit({ "diagnostic snapshot" }, { it() }, { assertEquals("diagnostic snapshot", it.getOrThrow()); delivered.countDown() }))
      assertTrue(delivered.await(2, TimeUnit.SECONDS))
      assertEquals(1L, release.count)
    } finally { release.countDown(); oem.shutdownNow(); exporter.shutdownNow() }
  }
  @Test fun failedWorkAndRejectedExecutorAllowRetry() {
    val preparation = DiagnosticPreparation<String>(java.util.concurrent.Executor { it.run() })
    var failure = false
    assertTrue(preparation.submit({ error("private storage unavailable") }, { it() }, { failure = it.isFailure }))
    assertTrue(failure)
    assertTrue(preparation.submit({ "retry" }, { it() }, {}))
    val rejected = DiagnosticPreparation<String>(java.util.concurrent.Executor { throw java.util.concurrent.RejectedExecutionException() })
    assertFalse(rejected.submit({ "unused" }, { it() }, {}))
    assertFalse(rejected.submit({ "retry rejected" }, { it() }, {}))
  }
}

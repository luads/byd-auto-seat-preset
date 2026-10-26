package dev.lua.seatpreset

import android.content.Context
import android.content.ContextWrapper
import android.test.InstrumentationTestCase
import java.io.File

/** Synthetic data in a separate directory. No vehicle SDK, ADB channel or remote delivery. */
class SeatEvidenceIntegrationTest:InstrumentationTestCase() {
  private lateinit var root:File
  private var revision=1L
  private val base get()=instrumentation.targetContext
  private fun context(locked:Boolean=false)=object:ContextWrapper(base) {
    override fun getFilesDir()=root
    override fun getSystemService(name:String):Any? = if(locked && name==Context.USER_SERVICE) null else super.getSystemService(name)

  }
  override fun setUp() { super.setUp();root=File(base.cacheDir,"evidence-integration");root.mkdirs();SeatSignatureInventory.clear(context()) }
  override fun tearDown() { SeatSignatureInventory.clear(context());root.deleteRecursively();super.tearDown() }
  fun testInventoryDeduplicatesAndRetriesFailedRevisionWithoutLosingPriorData() {
    var loads=0
    val first=SeatSignatureInventory.ensureRevision(context(),"fixture-revision-$revision") { loads++;"fixture-first-signature" }
    assertEquals(first,SeatSignatureInventory.ensureRevision(context(),"fixture-revision-$revision") { loads++;error("must be reused") })
    assertEquals(1,loads)
    revision=2
    assertTrue(runCatching { SeatSignatureInventory.ensureRevision(context(),"fixture-revision-$revision") { error("injected loader failure") } }.isFailure)
    assertTrue(SeatSignatureInventory.snapshot(context()).first.contains("fixture-first-signature"))
    val second=SeatSignatureInventory.ensureRevision(context(),"fixture-revision-$revision") { loads++;"fixture-second-signature" }
    assertFalse(first==second);assertEquals(2,loads)
    val snapshot=SeatSignatureInventory.snapshot(context())
    assertTrue(snapshot.first.contains("fixture-second-signature"));assertFalse(snapshot.first.contains("fixture-first-signature"));assertTrue(snapshot.second.isEmpty())
  }
  fun testLockedInventoryNeverCallsLoaderAndClearWaitsForUnlock() {
    SeatSignatureInventory.ensureRevision(context(),"fixture-revision-$revision") { "fixture-private-signature" }
    var called=false
    assertTrue(runCatching { SeatSignatureInventory.ensureRevision(context(true),"fixture-revision-$revision") { called=true;"unwanted" } }.isFailure)
    assertFalse(called)
    SeatSignatureInventory.clear(context(true))
    assertEquals("",SeatSignatureInventory.snapshot(context()).first)
  }
  fun testTruncatedInventoryIsFlaggedAndCanBeRebuilt() {
    SeatSignatureInventory.ensureRevision(context(),"fixture-revision-$revision") { "fixture-signature" }
    val file=File(root,"seat-signatures.txt");file.writeText(file.readText().substringBefore("[inventory complete]"))
    assertTrue(SeatSignatureInventory.snapshot(context()).second.isNotEmpty())
    var loaded=false
    SeatSignatureInventory.ensureRevision(context(),"fixture-revision-$revision") { loaded=true;"fixture-recovered" }
    assertTrue(loaded);assertTrue(SeatSignatureInventory.snapshot(context()).first.contains("fixture-recovered"))
  }
}

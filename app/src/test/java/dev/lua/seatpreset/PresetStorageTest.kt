package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class PresetStorageTest {
  private val doc = PresetDocument(listOf(Preset("one","Driver 1",Position(mapOf("demo-axis" to 25.0),"simulation","simulation-v1"),1234),Preset("two","Driver 2",Position(mapOf("demo-axis" to 75.0),"simulation","simulation-v1"),2345)),listOf("two","one"))
  private class Disk:PresetPersistence {
    var current:String?=null;var backup:String?=null;var fail=false
    override fun read()=current
    override fun previous()=backup
    override fun write(current:String,previous:String?) { if(fail) throw java.io.IOException("Interrupted save"); backup=previous;this.current=current }
  }
  private fun rejected(block:()->Unit) { try { block();fail("Expected rejection") }catch(_: IllegalArgumentException){}catch(_: org.json.JSONException){} }
  @Test fun exportRoundTripPreservesCoordinatesMetadataNamesAndFavourites() { assertEquals(doc,PresetCodec.decode(PresetCodec.encode(doc))) }
  @Test fun legacyMigrationKeepsSavedDataAndSwappedFavourites() { assertEquals(doc,PresetCodec.migrate(PresetCodec.array(doc.presets).toString(),"two","one")) }
  @Test fun legacyMissingFavouriteRepairsOnlyTheMapping() { assertEquals(listOf("one","two"),PresetCodec.migrate(PresetCodec.array(doc.presets).toString(),"gone","gone").favourites) }
  @Test fun invalidDocumentsAreRejected() {
    listOf(doc.copy(presets=listOf(doc.presets[0],doc.presets[0])),doc.copy(favourites=listOf("one","one")),doc.copy(favourites=listOf("one","gone")),doc.copy(presets=doc.presets.map{it.copy(name=" ")}),doc.copy(presets=doc.presets.map{it.copy(capturedAtMs=-1)}),doc.copy(presets=doc.presets.map{it.copy(position=it.position!!.copy(coordinates=mapOf("axis" to Double.NaN)))})).forEach { rejected { PresetCodec.encode(it) } }
  }
  @Test fun truncatedFutureOversizeAndWrongNumericTypesAreRejected() {
    val raw=PresetCodec.encode(doc)
    listOf(raw.take(raw.length/2),raw.replace("\"schema\":1","\"schema\":2")," ".repeat(PresetCodec.MAX_BYTES+1),raw.replace("25","\"25\""),raw.replace("1234","1.5"),raw+" trailing",raw.replace("\"Driver 1\"","123")).forEach { rejected{PresetCodec.decode(it)} }
  }
  @Test fun interruptedSaveLeavesPreviousDocumentAndRestartLoadsIt() {
    val d=Disk();val r=PresetRepository(d){PresetCodec.defaults()};r.save(doc);d.fail=true
    try{r.save(doc.copy(presets=doc.presets.map{it.copy(name="Changed")}));fail("Expected failure")}catch(_:java.io.IOException){}
    assertEquals(doc,PresetRepository(d){PresetCodec.defaults()}.load().document)
  }
  @Test fun corruptionRecoversPreviousWithoutOverwritingEvidence() {
    val d=Disk();val r=PresetRepository(d){PresetCodec.defaults()};r.save(doc);r.save(doc.copy(presets=doc.presets.map{it.copy(name="Changed")}));d.current="{truncated"
    val recovered=r.load();assertTrue(recovered.recovered);assertEquals(doc,recovered.document);assertEquals("{truncated",d.current)
  }
  @Test fun damagedPrimaryAndBackupBlockNormalSaveButConfirmedImportRecovers() {
    val d=Disk().apply{current="bad";backup="bad"};val r=PresetRepository(d){PresetCodec.defaults()}
    try{r.save(doc);fail("Expected failure")}catch(_:Exception){}
    assertEquals("bad",d.current);r.restore(PresetCodec.encode(doc));assertEquals(doc,r.load().document)
  }
  @Test fun invalidImportLeavesSavedStateUntouched() {
    val d=Disk();val r=PresetRepository(d){PresetCodec.defaults()};r.save(doc);rejected{r.restore("bad")};assertEquals(doc,r.load().document)
  }
  @Test fun importedDemoGeometryMustMatchKnownAxisAndRangeBeforeRecall() {
    val position = doc.presets[0].position!!
    assertTrue(DemoCoordinates.accepts(position))
    listOf(mapOf("demo-axis" to -1.0),mapOf("demo-axis" to 101.0),mapOf("demo-axis" to 25.5),mapOf("other-axis" to 25.0),mapOf("demo-axis" to 25.0,"extra-axis" to 1.0)).forEach { assertFalse(DemoCoordinates.accepts(position.copy(coordinates=it))) }
  }
  @Test fun backupDoesNotExportRuntimeSettingsOrRequests() {
    val raw=PresetCodec.encode(doc);listOf("developer-mode","adbkey","request","startup","check-updates").forEach{assertFalse(raw.contains(it))}
  }
}

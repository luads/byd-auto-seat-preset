package dev.lua.seatpreset
import android.content.pm.ProviderInfo
import android.net.Uri
import android.test.InstrumentationTestCase
import java.io.File
class BackupIntegrationTest: InstrumentationTestCase() {
  fun testProviderSharesOnlyExactBackupAndOnlyReadAccess() {
    val c=instrumentation.targetContext
    val directory=File(c.cacheDir,"backups").apply { mkdirs() }
    val name="backup-00000000-0000-0000-0000-000000000000.json"
    val file=File(directory,name);file.writeText(PresetCodec.encode(PresetCodec.defaults()))
    val provider=BackupProvider();provider.attachInfo(c,ProviderInfo().apply { authority="${c.packageName}.backups";exported=false;grantUriPermissions=true })
    val uri=Uri.parse("content://${c.packageName}.backups/$name")
    try {
      assertEquals("application/json",provider.getType(uri))
      provider.openFile(uri,"r").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { assertEquals(file.readText(),it.readBytes().toString(Charsets.UTF_8)) } }
      assertTrue(runCatching { provider.openFile(uri,"w") }.isFailure)
      for(path in listOf("../panel-adbkey","private-observations.log","update.apk")) assertTrue(runCatching { provider.getType(Uri.parse("content://${c.packageName}.backups/$path")) }.isFailure)
      assertTrue(runCatching { provider.getType(Uri.parse("content://other.backups/$name")) }.isFailure)
    } finally { file.delete() }
  }
}

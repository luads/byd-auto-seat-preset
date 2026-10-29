@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.lua.seatpreset

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

internal data class PresetChoice(val id:String,val name:String)
internal data class SettingsUiState(
  val presets:List<PresetChoice>,val favourites:List<PresetChoice>,val storageMessage:String?,
  val movementEnabled:Boolean,val movementPaused:Boolean,val homeReady:Boolean,val homeEnabled:Boolean,
  val panelWidth:Int,val panelMessage:String,val panelBusy:Boolean,val childEnabled:Boolean,val leftPresetId:String?,val rightPresetId:String?,
  val startupEnabled:Boolean,val startupGranted:Boolean,val startupStatus:String,
  val checkOnStart:Boolean,val autoUpdates:Boolean,val autoUpdateStatus:String?,val updateMessage:String,val updateBusy:Boolean,
  val releaseVersion:String?,val developer:Boolean,val canExport:Boolean,val canImport:Boolean,val demo:Boolean,val version:String,
)
internal enum class SettingsToggle { MOVEMENT, HOME, CHILD, STARTUP, CHECK_UPDATES, AUTO_UPDATES, DEVELOPER }
internal enum class SettingsCommand { BACK, SETUP_HOME, RESET_PANEL, CAR_STARTUP, CHECK_UPDATES, INSTALL, INSTALLER, INSPECT,
  PRIVATE_REPORT, CLEAR_PRIVATE, SETUP_LOG, COPY_LOG, EXPORT, IMPORT, RESTORE_CONNECTION, COPY_SETUP, DEMO, MOTION, KEEP_RECOVERY, SETUP_READS, TRUST_LAB, SEND_DIAGNOSTICS, SEAT_TRIALS, BACKREST_TEST }
internal sealed interface SettingsAction {
  data class Command(val command:SettingsCommand):SettingsAction
  data class Toggle(val setting:SettingsToggle,val enabled:Boolean):SettingsAction
  data class Rename(val id:String,val name:String):SettingsAction
  data class Favourite(val slot:Int,val id:String):SettingsAction
  data class Bind(val side:ChildSide,val id:String?):SettingsAction
  data class PanelWidth(val width:Int):SettingsAction
}
private sealed interface SettingsDialog {
  data class Rename(val preset:PresetChoice):SettingsDialog
  data class Favourite(val slot:Int):SettingsDialog
  data class Bind(val side:ChildSide):SettingsDialog
  data class Confirm(val action:SettingsAction,val title:String,val message:String,val accept:String):SettingsDialog
}
private val Canvas=Color(0xFF14171B)
private val Card=Color(0xFF20252B)
private val Raised=Color(0xFF29313A)
private val Silver=Color(0xFFE5E9EF)
private val Muted=Color(0xFFA8B3C1)
private val Blue=Color(0xFF5AA8ED)
private val Border=Color(0xFF353E48)

@Composable
internal fun SeatSettingsScreen(state:SettingsUiState,onAction:(SettingsAction)->Unit) {
  MaterialTheme(colorScheme=darkColorScheme(primary=Blue,onPrimary=Canvas,background=Canvas,onBackground=Silver,
    surface=Card,onSurface=Silver,surfaceVariant=Raised,onSurfaceVariant=Muted,outline=Border,secondaryContainer=Raised,onSecondaryContainer=Silver)) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    fun command(value:SettingsCommand) { onAction(SettingsAction.Command(value)) }
    val confirm:(SettingsAction,String,String,String)->Unit={ action,title,message,accept -> dialog=SettingsDialog.Confirm(action,title,message,accept) }
    Surface(Modifier.fillMaxSize(),color=Canvas,contentColor=Silver) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Canvas)) {
      val wide=maxWidth>=1000.dp
      Column(Modifier.align(Alignment.TopCenter).widthIn(max=1280.dp).fillMaxSize().padding(horizontal=32.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=100.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
          Column { Text("Settings",fontSize=28.sp,fontWeight=FontWeight.Medium);Text("Seat Presets · ${state.version}",fontSize=14.sp,color=Muted) }
          TonalAction("Presets",onClick={ command(SettingsCommand.BACK) })
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
          if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.Top) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)) {
              Drivers(state,onAction,{ dialog=SettingsDialog.Rename(it) },{ dialog=SettingsDialog.Favourite(it) },confirm)
              ChildShortcuts(state,onAction) { dialog=SettingsDialog.Bind(it) }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)) {
              HomePanel(state,onAction,confirm);StartupCard(state,onAction);UpdatesCard(state,onAction,confirm)
            }
          } else {
            Drivers(state,onAction,{ dialog=SettingsDialog.Rename(it) },{ dialog=SettingsDialog.Favourite(it) },confirm)
            HomePanel(state,onAction,confirm);ChildShortcuts(state,onAction) { dialog=SettingsDialog.Bind(it) };StartupCard(state,onAction);UpdatesCard(state,onAction,confirm)
          }
          Section("Advanced") { ToggleRow("Developer tools",state.developer) { onAction(SettingsAction.Toggle(SettingsToggle.DEVELOPER,it)) } }
          if(state.developer) {
            if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.Top) {
              Box(Modifier.weight(1f)) { DiagnosticsCard(state,onAction,confirm) };Box(Modifier.weight(1f)) { ConnectionCard(state,onAction,confirm) }
            } else { DiagnosticsCard(state,onAction,confirm);ConnectionCard(state,onAction,confirm) }
          }
        }
      }
    }
    }
    when(val open=dialog) {
      is SettingsDialog.Rename -> {
        var name by rememberSaveable(open.preset.id) { mutableStateOf(open.preset.name) }
        AlertDialog(onDismissRequest={ dialog=null },title={ Text("Driver name") },
          text={ OutlinedTextField(value=name,onValueChange={ if(it.length<=24) name=it },singleLine=true,label={ Text("Name") }) },
          confirmButton={ TextButton(modifier=Modifier.heightIn(min=76.dp),enabled=name.trim().isNotEmpty(),onClick={ onAction(SettingsAction.Rename(open.preset.id,name.trim()));dialog=null }) { Text("Save") } },
          dismissButton={ TextButton(modifier=Modifier.heightIn(min=76.dp),onClick={ dialog=null }) { Text("Cancel") } })
      }
      is SettingsDialog.Favourite -> SelectionDialog("Favourite ${open.slot+1}",state.presets,state.favourites[open.slot].id,false,{ dialog=null }) {
        onAction(SettingsAction.Favourite(open.slot,checkNotNull(it)));dialog=null
      }
      is SettingsDialog.Bind -> SelectionDialog(if(open.side==ChildSide.LEFT) "Left button" else "Right button",state.presets,
        if(open.side==ChildSide.LEFT) state.leftPresetId else state.rightPresetId,true,{ dialog=null }) { onAction(SettingsAction.Bind(open.side,it));dialog=null }
      is SettingsDialog.Confirm -> AlertDialog(onDismissRequest={ dialog=null },title={ Text(open.title) },text={ Text(open.message) },
        confirmButton={ TextButton(modifier=Modifier.heightIn(min=76.dp),onClick={ dialog=null;onAction(open.action) }) { Text(open.accept) } },
        dismissButton={ TextButton(modifier=Modifier.heightIn(min=76.dp),onClick={ dialog=null }) { Text("Cancel") } })
      null -> Unit
    }
  }
}
@Composable private fun Section(title:String,content:@Composable ColumnScope.()->Unit) {
  Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Card,border=BorderStroke(1.dp,Border.copy(alpha=.55f))) {
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      Text(title,fontSize=22.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(bottom=4.dp));content()
    }
  }
}
@Composable private fun Caption(text:String) { Text(text,fontSize=15.sp,color=Muted,lineHeight=21.sp) }
@Composable private fun DividerLine() { HorizontalDivider(color=Border,modifier=Modifier.padding(vertical=4.dp)) }
@Composable private fun TonalAction(label:String,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit) {
  FilledTonalButton(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(14.dp),modifier=modifier.heightIn(min=76.dp),
    colors=ButtonDefaults.filledTonalButtonColors(containerColor=Raised,contentColor=Silver),contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp)) {
    Text(label,fontSize=17.sp,lineHeight=22.sp)
  }
}
@Composable private fun ActionPair(first:String,second:String,onFirst:()->Unit,onSecond:()->Unit) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    if(maxWidth>=420.dp) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      TonalAction(first,Modifier.weight(1f),onClick=onFirst);TonalAction(second,Modifier.weight(1f),onClick=onSecond)
    } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
      TonalAction(first,Modifier.fillMaxWidth(),onClick=onFirst);TonalAction(second,Modifier.fillMaxWidth(),onClick=onSecond)
    }
  }
}
@Composable private fun ToggleRow(title:String,checked:Boolean,changed:(Boolean)->Unit) {
  Row(Modifier.fillMaxWidth().heightIn(min=76.dp).toggleable(value=checked,role=Role.Switch,onValueChange=changed),
    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
    Text(title,Modifier.weight(1f),fontSize=17.sp,lineHeight=23.sp)
    Switch(checked=checked,onCheckedChange=null,colors=SwitchDefaults.colors(checkedThumbColor=Canvas,checkedTrackColor=Blue,
      uncheckedThumbColor=Muted,uncheckedTrackColor=Raised,uncheckedBorderColor=Border))
  }
}
@Composable private fun Drivers(state:SettingsUiState,action:(SettingsAction)->Unit,rename:(PresetChoice)->Unit,choose:(Int)->Unit,confirm:(SettingsAction,String,String,String)->Unit) {
  Section("Drivers") {
    state.favourites.forEachIndexed { slot,preset ->
      if(slot>0) DividerLine()
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(role=Role.Button) { choose(slot) }
          .heightIn(min=100.dp).padding(vertical=8.dp,horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
          Text("FAVOURITE ${slot+1}",color=Muted,fontSize=12.sp,letterSpacing=.8.sp)
          Text(preset.name,fontSize=24.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
          Text("Change preset  ›",fontSize=13.sp,color=Blue)
        }
        TonalAction("Rename",onClick={ rename(preset) })
      }
    }
    state.storageMessage?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=15.sp)
      if(it.startsWith("Recovered")) TonalAction("Confirm recovered presets",Modifier.fillMaxWidth()) { confirm(SettingsAction.Command(SettingsCommand.KEEP_RECOVERY),"Keep recovered presets?","Check recovered names and positions. No seat movement.","Keep presets") }
    }
    DividerLine();Text("Seat movement",fontSize=19.sp,fontWeight=FontWeight.Medium)
    ToggleRow("Allow seat movement",state.movementEnabled) { enabled ->
      val change=SettingsAction.Toggle(SettingsToggle.MOVEMENT,enabled)
      if(!enabled) action(change) else confirm(change,"Allow seat movement?",
        if(state.movementPaused) "Enable owner-triggered native seat signals in P with the parking brake confirmed. This switch does not move the seat."
        else "Allow owner-triggered preset loading in P with the parking brake applied. Keep native controls within reach. Enabling this switch does not move the seat.","Enable")
    }
    if(state.movementPaused) Text("The previous command path is blocked. Your choice is saved.",fontSize=15.sp,color=Color(0xFFE5B765),lineHeight=21.sp)
    else Caption("Requires P and the parking brake.")
  }
}
@Composable private fun HomePanel(state:SettingsUiState,action:(SettingsAction)->Unit,confirm:(SettingsAction,String,String,String)->Unit) {
  Section("Home panel") {
    Caption("Two favourites, visible on home while in P.")
    if(!state.homeReady || state.panelBusy) TonalAction("Set up home panel",Modifier.fillMaxWidth(),!state.panelBusy) { confirm(SettingsAction.Command(SettingsCommand.SETUP_HOME),"Enable home access?","Allow the panel on home. Approve the Android prompt if shown. No seat movement.","Set up") }
    if(state.panelMessage.isNotBlank()) Caption(state.panelMessage)
    ToggleRow("Show on home in P",state.homeEnabled) { action(SettingsAction.Toggle(SettingsToggle.HOME,it)) }
    var size by remember(state.panelWidth) { mutableFloatStateOf(state.panelWidth.toFloat()) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
      val width=minOf(size.dp,maxWidth,360.dp)
      key(state.favourites) {
        AndroidView(factory={ context ->
          runCatching { FloatingPanelView.create(context,
            { android.widget.Toast.makeText(context,"Preview only",android.widget.Toast.LENGTH_SHORT).show() },
            { android.widget.Toast.makeText(context,"Appearance preview only",android.widget.Toast.LENGTH_SHORT).show() }) }
            .getOrElse { android.widget.TextView(context).apply { text="Panel preview unavailable" } }
        },modifier=Modifier.width(width).height(width/2))
      }
    }
    TonalAction(if(appearance) "Hide appearance" else "Panel appearance",Modifier.fillMaxWidth()) { appearance=!appearance }
    if(appearance) {
      Caption("Panel size · ${size.toInt()}")
      Slider(value=size,onValueChange={ size=it },valueRange=320f..520f,
        onValueChangeFinished={ action(SettingsAction.PanelWidth(size.toInt())) },modifier=Modifier.fillMaxWidth().heightIn(min=60.dp))
      TonalAction("Reset panel position",Modifier.fillMaxWidth()) { action(SettingsAction.Command(SettingsCommand.RESET_PANEL)) }
    }
  }
}
@Composable private fun ShortcutTile(title:String,name:String,modifier:Modifier=Modifier,click:()->Unit) {
  Column(modifier.clip(RoundedCornerShape(18.dp)).background(Raised).clickable(role=Role.Button,onClick=click).heightIn(min=140.dp).padding(16.dp),
    verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Text(title,color=Muted,fontSize=14.sp)
    Text(name,fontSize=22.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
    Text("Double press  ·  Change",fontSize=13.sp,color=Blue)
  }
}
@Composable private fun ChildShortcuts(state:SettingsUiState,action:(SettingsAction)->Unit,choose:(ChildSide)->Unit) {
  fun name(id:String?)=state.presets.firstOrNull { it.id==id }?.name ?: "Not assigned"
  Section("Child-lock shortcuts") {
    Caption("Assign a preset to each double press.")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      if(maxWidth>=420.dp) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        ShortcutTile("Left button",name(state.leftPresetId),Modifier.weight(1f)) { choose(ChildSide.LEFT) }
        ShortcutTile("Right button",name(state.rightPresetId),Modifier.weight(1f)) { choose(ChildSide.RIGHT) }
      } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        ShortcutTile("Left button",name(state.leftPresetId),Modifier.fillMaxWidth()) { choose(ChildSide.LEFT) }
        ShortcutTile("Right button",name(state.rightPresetId),Modifier.fillMaxWidth()) { choose(ChildSide.RIGHT) }
      }
    }
    ToggleRow("Use double press in P",state.childEnabled) { action(SettingsAction.Toggle(SettingsToggle.CHILD,it)) }
    Caption(if(state.movementPaused) "Mappings are kept. Seat movement is paused." else "Runs only in P with Seat movement enabled.")
  }
}
@Composable private fun StartupCard(state:SettingsUiState,action:(SettingsAction)->Unit) {
  Section("Startup") {
    Caption("Allow Seat Presets in the car's startup manager.")
    TonalAction("Car startup settings",Modifier.fillMaxWidth()) { action(SettingsAction.Command(SettingsCommand.CAR_STARTUP)) }
    ToggleRow("Restore connection after restart",state.startupEnabled) { action(SettingsAction.Toggle(SettingsToggle.STARTUP,it)) }
  }
}
@Composable private fun UpdatesCard(state:SettingsUiState,action:(SettingsAction)->Unit,confirm:(SettingsAction,String,String,String)->Unit) {
  Section("Updates") {
    ToggleRow("Check on start",state.checkOnStart) { action(SettingsAction.Toggle(SettingsToggle.CHECK_UPDATES,it)) }
    ToggleRow("Install automatically",state.autoUpdates) { enabled ->
      val change=SettingsAction.Toggle(SettingsToggle.AUTO_UPDATES,enabled)
      if(!enabled) action(change) else confirm(change,"Automatic updates","Install verified releases when the app starts. Requires completed home access setup. Presets are kept.","Enable")
    }
    state.autoUpdateStatus?.let { Caption(it) };Caption(state.updateMessage)
    TonalAction("Check updates",Modifier.fillMaxWidth(),!state.updateBusy) { action(SettingsAction.Command(SettingsCommand.CHECK_UPDATES)) }
    state.releaseVersion?.let { version ->
      TonalAction("Install $version",Modifier.fillMaxWidth(),!state.updateBusy) { action(SettingsAction.Command(SettingsCommand.INSTALL)) }
      TonalAction("Android installer",Modifier.fillMaxWidth(),!state.updateBusy) { action(SettingsAction.Command(SettingsCommand.INSTALLER)) }
    }
  }
}
@Composable private fun DiagnosticsCard(state:SettingsUiState,action:(SettingsAction)->Unit,confirm:(SettingsAction,String,String,String)->Unit) {
  fun send(command:SettingsCommand) { action(SettingsAction.Command(command)) }
  Section("Diagnostics") {
    TonalAction(if(state.demo) "Seat trials · simulation" else "Seat trials",Modifier.fillMaxWidth()) { send(SettingsCommand.BACKREST_TEST) }
    if(!state.demo) TonalAction("Inspect saved positions",Modifier.fillMaxWidth()) { send(SettingsCommand.INSPECT) }
    if (!state.demo) {
    TonalAction("Trusted ADB Lab certificate",Modifier.fillMaxWidth()) { send(SettingsCommand.TRUST_LAB) }
    TonalAction("Send diagnostics to ADB Lab",Modifier.fillMaxWidth()) { confirm(SettingsAction.Command(SettingsCommand.SEND_DIAGNOSTICS),"Prepare private diagnostics?","This transfers private seat readings, preset IDs, timing and failures to the trusted ADB Lab. It excludes preset backups and setup credentials. ADB Lab asks again before any Telegram send. No seat operation.","Prepare diagnostics") }
    }
    ActionPair("View private report","Clear private report",{ send(SettingsCommand.PRIVATE_REPORT) },{ confirm(SettingsAction.Command(SettingsCommand.CLEAR_PRIVATE),"Clear private observations?","Remove saved and pending private diagnostic entries. Presets are kept.","Clear") })
    ActionPair("View setup log","Copy setup log",{ send(SettingsCommand.SETUP_LOG) },{ send(SettingsCommand.COPY_LOG) })
    if(state.canExport || state.canImport) {
      DividerLine();Text("Preset backups",fontSize=18.sp)
      if(state.canExport && state.canImport) ActionPair("Export presets","Import presets",{ send(SettingsCommand.EXPORT) },{ send(SettingsCommand.IMPORT) })
      else if(state.canExport) TonalAction("Export presets",Modifier.fillMaxWidth()) { send(SettingsCommand.EXPORT) }
      else TonalAction("Import presets",Modifier.fillMaxWidth()) { send(SettingsCommand.IMPORT) }
    }
  }
}
@Composable private fun ConnectionCard(state:SettingsUiState,action:(SettingsAction)->Unit,confirm:(SettingsAction,String,String,String)->Unit) {
  fun send(command:SettingsCommand) { action(SettingsAction.Command(command)) }
  Section("Connection") {
    if(!state.demo) TonalAction("Set up vehicle readings",Modifier.fillMaxWidth()) {
      confirm(SettingsAction.Command(SettingsCommand.SETUP_READS),"Allow vehicle readings?",
        "Use the local setup connection to grant this app read access for the seat, gear, parking brake and child locks. Approve the Android debugging prompt if shown. This does not grant seat-write access or move anything.","Set up")
    }
    Caption(state.startupStatus);Text(if(state.startupGranted) "Startup access granted" else "Startup access needs setup",fontSize=16.sp)
    ActionPair("Restore connection now","Car startup settings",{ send(SettingsCommand.RESTORE_CONNECTION) },{ send(SettingsCommand.CAR_STARTUP) })
    if(!state.startupGranted) TonalAction("Copy setup command",Modifier.fillMaxWidth()) { send(SettingsCommand.COPY_SETUP) }
    if(state.demo) { DividerLine();ActionPair("Demo controls","Preview panel motion",{ send(SettingsCommand.DEMO) },{ send(SettingsCommand.MOTION) }) }
  }
}
@Composable private fun SelectionDialog(title:String,choices:List<PresetChoice>,selected:String?,allowNone:Boolean,dismiss:()->Unit,choose:(String?)->Unit) {
  AlertDialog(onDismissRequest=dismiss,title={ Text(title) },text={
    Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
      val values=if(allowNone) listOf(PresetChoice("","Not assigned"))+choices else choices
      values.forEach { item ->
        val id=item.id.takeIf { it.isNotEmpty() }
        Row(Modifier.fillMaxWidth().heightIn(min=76.dp).clickable(role=Role.RadioButton) { choose(id) },
          verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
          RadioButton(selected=selected==id,onClick=null);Text(item.name,fontSize=18.sp)
        }
      }
    }
  },confirmButton={ TextButton(modifier=Modifier.heightIn(min=76.dp),onClick=dismiss) { Text("Cancel") } })
}

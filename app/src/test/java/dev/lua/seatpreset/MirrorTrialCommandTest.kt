package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class MirrorTrialCommandTest {
  private fun frame()=PositionFeedback.properties.keys.associateWith { PositionFeedback.Sample(50.0,100L) }
  @Test fun onlyEightSdkDeclaredTiltActionsAndReleaseAreAvailable() {
    val actions=MirrorTrialCommand.axes.flatMap { axis->listOf(MirrorTrialCommand.action(axis,1),MirrorTrialCommand.action(axis,2)) }
    assertEquals((1..8).toList(),actions)
    assertEquals((1..8).toList(),MirrorTrialCommand.constants.values.toList())
    MirrorTrialCommand.axes.forEach { assertEquals(0,MirrorTrialCommand.action(it,0)) }
    for(axis in listOf("horizontal","fold","mirror.both.x","mirror.left.z")) assertThrows(IllegalArgumentException::class.java) { MirrorTrialCommand.action(axis,1) }
    assertThrows(IllegalArgumentException::class.java) { MirrorTrialCommand.action("mirror.left.x",3) }
  }
  @Test fun missingOrInvalidFeedbackRejectsTheTrial() {
    val f=frame()
    for(bad in listOf(f-"mirror.right.y",f-"height",f+("mirror.left.x" to PositionFeedback.Sample(Double.NaN,100)),f+("mirror.left.x" to PositionFeedback.Sample(50.0,0))))
      assertThrows(IllegalStateException::class.java) { MirrorTrialCommand.requireFeedback("mirror.left.x",bad) }
  }
  @Test fun onlySelectedAxisChangeCanBeConfirmedAndDoesNotEnablePresetRecall() {
    val f=frame();val changed=f+("mirror.left.x" to PositionFeedback.Sample(51.0,101))
    assertTrue(MirrorTrialCommand.changedOnlySelected("mirror.left.x",f,changed))
    assertFalse(MirrorTrialCommand.changedOnlySelected("mirror.left.x",f,f))
    assertFalse(MirrorTrialCommand.changedOnlySelected("mirror.left.x",f,changed+("height" to PositionFeedback.Sample(51.0,101))))
    assertFalse(MirrorTrialCommand.changedOnlySelected("mirror.left.x",f,changed+("mirror.right.x" to PositionFeedback.Sample(51.0,101))))
    assertFalse(MirrorTrialCommand.changedOnlySelected("mirror.left.x",f,changed+("mirror.left.x" to PositionFeedback.Sample(51.0,100))))
    assertFalse("mirror.left.x" in PositionFeedback.seatAxes)
  }
}

package dev.lua.seatpreset
import org.junit.Assert.*
import org.junit.Test
class HomePanelPolicyTest {
  @Test fun exactHomeOnly() {
    assertTrue(HomePanelPolicy.isHome("home", "home.Main", "home", "home.Main"))
    for (pair in listOf(null to null, "home" to "home.Popup", "camera" to "camera.Main", "systemui" to "card.Window"))
      assertFalse(HomePanelPolicy.isHome("home", "home.Main", pair.first, pair.second))
    assertFalse(HomePanelPolicy.isHome(null, null, null, null))
  }
  @Test fun enrollmentPreservesOthers() {
    assertEquals("a/Service:b/Service:own/Service", HomePanelPolicy.services("a/Service:own/Service:b/Service", "own/Service", true))
    assertEquals("a/Service:b/Service", HomePanelPolicy.services("a/Service:own/Service:b/Service", "own/Service", false))
    assertEquals("", HomePanelPolicy.services(null, "own/Service", false))
  }
}

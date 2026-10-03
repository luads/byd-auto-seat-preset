package dev.lua.seatpreset

import org.junit.Assert.*
import org.junit.Test

class FavouriteSlotsTest {
  @Test fun selectingTheOtherDriverSwapsInsteadOfDuplicating() {
    assertEquals(listOf("two", "one"), FavouriteSlots.assign(listOf("one", "two"), 0, "two"))
    assertEquals(listOf("two", "one"), FavouriteSlots.assign(listOf("one", "two"), 1, "one"))
  }
  @Test fun addingAnotherPresetKeepsTheOtherFavourite() {
    assertEquals(listOf("one", "three"), FavouriteSlots.assign(listOf("one", "two"), 1, "three"))
  }
  @Test(expected = IllegalArgumentException::class) fun invalidSlotCannotReplaceEitherDriver() {
    FavouriteSlots.assign(listOf("one", "two"), 2, "three")
  }
}

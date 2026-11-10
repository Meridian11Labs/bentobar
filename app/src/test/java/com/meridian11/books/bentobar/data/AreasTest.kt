package com.meridian11.books.bentobar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a layout says about the bar's three areas: which are in use, and which one is home to the bar's one-off things. */
class AreasTest {
    private fun item(id: String, area: Position, section: Section = Section.SHOWN, type: String = "clock") = ItemConfig(id, type, section, area = area)
    private fun layout(vararg items: ItemConfig) = BarConfig(items = items.toList())
    /** A type from a later version: in the layout, drawn nowhere. */
    private val later = "a-type-of-a-later-version"

    @Test fun anAreaIsInUseWhenItHoldsAnItemOutsideOffOfATypeThisVersionKnows() {
        val c = layout(item("l", Position.LEFT), item("m", Position.CENTER, Section.HIDDEN), item("r", Position.RIGHT, Section.OFF))
        assertEquals(setOf(Position.LEFT, Position.CENTER), c.areasInUse()) // a Hidden item counts, an Off one doesn't
        assertEquals(setOf(Position.LEFT), layout(item("l", Position.LEFT), item("x", Position.RIGHT, type = later)).areasInUse())
        assertEquals(emptySet<Position>(), layout(item("o", Position.LEFT, Section.OFF)).areasInUse())
        assertEquals(emptySet<Position>(), BarConfig().areasInUse())
    }

    @Test fun theHomeAreaIsRightWhileRightIsInUse() {
        assertEquals(Position.RIGHT, layout(item("l", Position.LEFT), item("m", Position.CENTER), item("r", Position.RIGHT, Section.HIDDEN)).homeArea())
    }

    @Test fun withoutRightTheHomeAreaIsMiddleAndOnlyThenLeft() {
        assertEquals(Position.CENTER, layout(item("l", Position.LEFT), item("m", Position.CENTER)).homeArea())
        assertEquals(Position.CENTER, layout(item("m", Position.CENTER)).homeArea())
        assertEquals(Position.LEFT, layout(item("l", Position.LEFT)).homeArea())
        // Off in Right and Middle: neither is in use.
        assertEquals(Position.LEFT, layout(item("r", Position.RIGHT, Section.OFF), item("m", Position.CENTER, Section.OFF), item("l", Position.LEFT)).homeArea())
    }

    @Test fun withEveryItemOffTheHomeAreaIsTheFirstItemsArea() {
        assertEquals(Position.LEFT, layout(item("l", Position.LEFT, Section.OFF), item("r", Position.RIGHT, Section.OFF)).homeArea())
        assertEquals(Position.CENTER, layout(item("m", Position.CENTER, Section.OFF), item("l", Position.LEFT, Section.OFF)).homeArea())
    }

    /** A layout with no items has no area to speak for it: the place the whole bar was last given does, as it always did. */
    @Test fun withNoItemsTheHomeAreaIsWherePositionSays() {
        assertEquals(Position.RIGHT, BarConfig().homeArea())
        for (position in Position.entries) assertEquals(position, BarConfig(position = position).homeArea())
    }

    @Test fun withAnItemPositionSaysNothingAboutTheHomeArea() {
        assertEquals(Position.RIGHT, layout(item("r", Position.RIGHT)).copy(position = Position.LEFT).homeArea())
        assertEquals(Position.CENTER, layout(item("m", Position.CENTER, Section.OFF)).copy(position = Position.LEFT).homeArea())
    }

    @Test fun anItemOfATypeThisVersionDoesntKnowDoesNotMakeItsAreaHome() {
        assertEquals(Position.LEFT, layout(item("x", Position.RIGHT, type = later), item("l", Position.LEFT)).homeArea())
        // Alone it is still the first item, and so says where the home area is, as an Off item does.
        assertEquals(Position.CENTER, layout(item("x", Position.CENTER, type = later)).homeArea())
    }

    @Test fun aLayoutUsesAreasWhenMoreThanOneHoldsAnItemOutsideOff() {
        assertTrue(layout(item("l", Position.LEFT), item("r", Position.RIGHT, Section.HIDDEN)).usesAreas())
        assertFalse(layout(item("l", Position.LEFT), item("l2", Position.LEFT, Section.HIDDEN)).usesAreas())
        assertFalse(layout(item("l", Position.LEFT), item("r", Position.RIGHT, Section.OFF)).usesAreas())
        assertFalse(BarConfig().usesAreas())
    }

    @Test fun theOneAreaIsWhereEveryItemOutsideOffIsAndNullWhenTheyAreInTwo() {
        assertEquals(Position.LEFT, layout(item("l", Position.LEFT), item("l2", Position.LEFT, Section.HIDDEN), item("r", Position.RIGHT, Section.OFF)).oneArea())
        assertNull(layout(item("l", Position.LEFT), item("m", Position.CENTER)).oneArea())
    }

    @Test fun withEveryItemOffTheOneAreaIsTheHomeArea() {
        val off = layout(item("m", Position.CENTER, Section.OFF), item("l", Position.LEFT, Section.OFF))
        assertEquals(off.homeArea(), off.oneArea())
        assertEquals(Position.CENTER, off.oneArea())
        assertEquals(Position.RIGHT, BarConfig().oneArea())
    }

    /** Look's row shows a place as chosen on a layout with no items too: the one Position says. */
    @Test fun withNoItemsTheOneAreaIsWherePositionSays() {
        for (position in Position.entries) assertEquals(position, BarConfig(position = position).oneArea())
    }

    @Test fun roomGoesRightLeftMiddleAndHomeGoesRightMiddleLeft() {
        assertEquals(listOf(Position.RIGHT, Position.LEFT, Position.CENTER), ROOM_ORDER)
        assertEquals(listOf(Position.RIGHT, Position.CENTER, Position.LEFT), HOME_ORDER)
    }
}

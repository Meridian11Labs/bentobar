package com.meridian11.books.bentobar.bar

import com.meridian11.books.bentobar.data.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Switch popups on hover" (off by default): with a popup open, pointing at another item opens its popup, as in a menu bar. */
class PopupRulesTest {
    @Test fun aPressOnTheOpenPopupsItemIsThatItemsOnceTheGoneItemsAreForgotten() {
        // A right-anchored strip after a meeting's item has gone: the others moved left, and "own" holds the open popup.
        val drawn = listOf(Span("clock", 0f, 100f), Span("own", 110f, 210f))
        assertEquals("own", PopupRules.pressedItem(150f, drawn))
        // What a box left behind would do: the press goes to nothing that is there, and the popup turns round instead of closing.
        assertEquals("meeting", PopupRules.pressedItem(150f, drawn + Span("meeting", 100f, 250f)))
    }

    @Test fun aTooltipKeepsThePillsHeightAtTheDefaultTextSizeAndGrowsWithALargerOne() {
        // 13 sp of Roboto is 1.327 em from top to bottom: 18 px at density 1, 20 at 1.125, 36 at 2.
        assertEquals(28, PopupRules.tooltipHeightPx(line = 18, density = 1f))
        assertEquals(31, PopupRules.tooltipHeightPx(line = 20, density = 1.125f))
        assertEquals(56, PopupRules.tooltipHeightPx(line = 36, density = 2f))
        // Twice the text at density 2: 72 px of it, and no clipping.
        assertTrue(PopupRules.tooltipHeightPx(line = 72, density = 2f) >= 72)
    }

    @Test fun aPressBetweenItemsGoesToTheNearerOne() {
        val drawn = listOf(Span("own", 100f, 200f), Span("next", 260f, 300f))
        assertEquals("own", PopupRules.pressedItem(220f, drawn))
        assertEquals("next", PopupRules.pressedItem(240f, drawn))
        assertEquals(null, PopupRules.pressedItem(5f, emptyList()))
    }

    @Test fun withTheSettingOnPointingAtAnotherItemOpensItsPopup() {
        assertTrue(PopupRules.hoverOpens(open = MenuKey.Item("weather1"), hovered = "calendar1", hasPopup = true, on = true))
        // From the ‹ menu too: it is the bar's own popup.
        assertTrue(PopupRules.hoverOpens(open = MenuKey.Bar, hovered = "calendar1", hasPopup = true, on = true))
    }

    @Test fun offByDefaultAndNeverWithoutAnOpenPopup() {
        assertFalse(PopupRules.hoverOpens(open = MenuKey.Item("weather1"), hovered = "calendar1", hasPopup = true, on = false))
        // Nothing open: hovering never opens anything (Alex: no hover surprises).
        assertFalse(PopupRules.hoverOpens(open = null, hovered = "calendar1", hasPopup = true, on = true))
    }

    @Test fun itNeverActsForAClickAndLeavesARightClickMenuAlone() {
        // An item that acts on a click (keep awake, a Shortcut) has no popup: hovering it must not act.
        assertFalse(PopupRules.hoverOpens(open = MenuKey.Item("weather1"), hovered = "caffeine1", hasPopup = false, on = true))
        // The item whose popup is open: nothing to switch.
        assertFalse(PopupRules.hoverOpens(open = MenuKey.Item("weather1"), hovered = "weather1", hasPopup = true, on = true))
        // A right-click menu belongs to its item: pointing elsewhere doesn't swap it for a popup.
        assertFalse(PopupRules.hoverOpens(open = MenuKey.ItemContext("weather1"), hovered = "calendar1", hasPopup = true, on = true))
    }

    // ---- which area's strip a press landed on ----------------------------------------------------------------

    /** Three strips in a 41 px bar: Left from 168, the Middle from 800, Right up to 1681. */
    private val frames = listOf(
        StripFrame(Position.LEFT, left = 168, top = 0, width = 200, height = 41),
        StripFrame(Position.CENTER, left = 800, top = 0, width = 250, height = 41),
        StripFrame(Position.RIGHT, left = 1381, top = 0, width = 300, height = 41),
    )

    @Test fun aPressBelongsToTheAreaWhoseStripItLandsOn() {
        assertEquals(Position.LEFT, PopupRules.pressedArea(200f, 20f, frames))
        assertEquals(Position.CENTER, PopupRules.pressedArea(925f, 20f, frames))
        assertEquals(Position.RIGHT, PopupRules.pressedArea(1680.5f, 40.5f, frames))
        // A strip's first pixel is its own and the one after its last is not, as the one strip's rule had it.
        assertEquals(Position.CENTER, PopupRules.pressedArea(800f, 0f, frames))
        assertNull(PopupRules.pressedArea(1050f, 20f, frames))
        assertNull(PopupRules.pressedArea(1681f, 20f, frames))
    }

    @Test fun aPressBetweenTwoStripsOrAboveOrBelowThemIsInNoArea() {
        assertNull("between Left and the Middle", PopupRules.pressedArea(500f, 20f, frames))
        assertNull("left of them all", PopupRules.pressedArea(100f, 20f, frames))
        assertNull("below the bar", PopupRules.pressedArea(925f, 41f, frames))
        assertNull("below the bar", PopupRules.pressedArea(925f, 300f, frames))
        // A bar that does not begin at the screen's top edge.
        val lower = frames.map { it.copy(top = 10) }
        assertNull("above the strips", PopupRules.pressedArea(925f, 9.5f, lower))
        assertEquals(Position.CENTER, PopupRules.pressedArea(925f, 10f, lower))
        assertNull("no strip is on screen", PopupRules.pressedArea(925f, 20f, emptyList()))
    }

    /** The window that hears presses outside the bar is one strip's: a press on another strip is outside that window, and on the bar. */
    @Test fun aPressInsideAnotherStripsFrameIsNotOutside() {
        // Heard by Right's window, as every press outside it is: on Left's strip and on the Middle's it is on the bar.
        assertEquals(Position.LEFT, PopupRules.pressedArea(170f, 5f, frames))
        assertEquals(Position.CENTER, PopupRules.pressedArea(1049f, 40f, frames))
        // With one strip on screen nothing its window hears as outside is on the bar: every such press folds, as it always did.
        val one = frames.filter { it.area == Position.RIGHT }
        for (x in listOf(0.5f, 170f, 925f, 1380.5f, 1681f, 1900f)) for (y in listOf(0.5f, 20f, 40.5f)) assertNull("$x, $y", PopupRules.pressedArea(x, y, one))
        for (y in listOf(41f, 500f)) assertNull("under the strip", PopupRules.pressedArea(1500f, y, one))
    }

    // ---- what a press outside the open popup is on -----------------------------------------------------------

    @Test fun aPressOnThePopupsOwnStripIsOnTheItemItNames() {
        // One strip, or the strip the popup hangs from: what the press is on is what the strip calls it, as it always was.
        for (area in Position.entries) for (item in listOf("chevron", "clock1", null)) assertEquals(item, PopupRules.pressedOwner(item, on = area, popup = area))
    }

    @Test fun anItemOfAnotherStripIsItselfAndAnotherStripsChevronIsNotTheChevronMenusOwn() {
        assertEquals("clock1", PopupRules.pressedOwner("clock1", on = Position.LEFT, popup = Position.RIGHT))
        assertNull(PopupRules.pressedOwner(null, on = Position.LEFT, popup = Position.RIGHT))
        // Every area has a ‹ and the ‹ menu hangs from one of them: a press on another's is a press on another item.
        val other = PopupRules.pressedOwner("chevron", on = Position.LEFT, popup = Position.RIGHT)
        assertTrue("$other", other != null && other != MenuKey.Bar.owner)
        val popups = PopupState<String>()
        popups.opened("w1", MenuKey.Bar)
        assertEquals("the menu dissolves, and the click opens that strip's", PopupCommand.Dissolve, popups.press(other, 100L))
        assertEquals("its own ‹ folds it and spends the click", PopupCommand.Fold, popups.press(PopupRules.pressedOwner("chevron", Position.RIGHT, Position.RIGHT), 100L))
        // An item's popup too: no item is called what another strip's ‹ is.
        popups.opened("w2", MenuKey.Item("clock1"))
        assertEquals(PopupCommand.Dissolve, popups.press(other, 100L))
    }

    @Test fun aPressWithoutCoordinatesIsInNoAreaEvenWithAStripInTheScreensCorner() {
        // A press on another app's window comes as 0, 0: it is not a press on a strip that begins there.
        val corner = listOf(StripFrame(Position.LEFT, left = 0, top = 0, width = 200, height = 41))
        assertNull(PopupRules.pressedArea(0f, 0f, corner))
        assertEquals(Position.LEFT, PopupRules.pressedArea(1f, 0f, corner))
        assertEquals(Position.LEFT, PopupRules.pressedArea(0f, 1f, corner))
    }
}

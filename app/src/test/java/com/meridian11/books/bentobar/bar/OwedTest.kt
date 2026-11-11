package com.meridian11.books.bentobar.bar

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.behindChevron
import com.meridian11.books.bentobar.data.carrierOverflow
import com.meridian11.books.bentobar.data.chevronOpensMenu
import com.meridian11.books.bentobar.data.homeArea
import com.meridian11.books.bentobar.data.notDrawn
import com.meridian11.books.bentobar.data.owedIds
import com.meridian11.books.bentobar.data.stripPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An area in use with no room has no window, and nothing the user put on the bar may be out of reach for that: what it
 * would draw now is owed to the first placed strip's ‹ (the carrier's), whose menu lists it, and Settings says of each
 * such item that it doesn't fit right now.
 */
class OwedTest {
    private val right = Position.RIGHT
    private val left = Position.LEFT
    private val middle = Position.CENTER
    private val nobody = { _: ItemConfig -> false }

    private val clock = ItemConfig("c", "clock", Section.SHOWN, area = right)
    private val memory = ItemConfig("m", "memory", Section.SHOWN, area = left)
    private val battery = ItemConfig("b", "battery", Section.HIDDEN, area = left)
    private val timerRule = ItemConfig("t", "timer", Section.HIDDEN, whenActive = true, area = left)
    private val off = ItemConfig("o", "cpu", Section.OFF, area = left)
    private val newer = ItemConfig("n", "from-a-newer-version", Section.SHOWN, area = left)
    private val date = ItemConfig("d", "calendar", Section.SHOWN, area = middle)
    private val layout = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(clock, memory, battery, timerRule, off, newer, date))

    // ---- what an area with no window owes --------------------------------------------------------------------

    @Test fun anOwedAreasShownItemsAreOwed() {
        assertEquals(setOf("m"), layout.owedIds(setOf(left), nobody, expanded = false))
        assertEquals(setOf("m", "d"), layout.owedIds(setOf(left, middle), nobody, expanded = false))
    }

    @Test fun anAreaWithAWindowOwesNothing() {
        assertEquals(emptySet<String>(), layout.owedIds(emptySet(), nobody, expanded = false))
        assertEquals("only the areas named", setOf("d"), layout.owedIds(setOf(middle), nobody, expanded = false))
    }

    /** A hidden item is behind ‹ already, and the ‹ menu lists it as hidden: it is owed only when its strip would draw it. */
    @Test fun anOwedAreasHiddenItemsAreOwedOnlyWhileOpenOrShownByTheirRule() {
        assertEquals("the bar is open: Left would bring out both", setOf("m", "b", "t"), layout.owedIds(setOf(left), nobody, expanded = true))
        assertEquals("its rule holds: it would be out on its own", setOf("m", "t"), layout.owedIds(setOf(left), { it.id == "t" }, expanded = false))
        val everything = layout.copy(hiddenMode = HiddenMode.SHOW_ALL)
        assertEquals("Show everything draws a hidden item, and a rule item when its rule holds", setOf("m", "b"),
            everything.owedIds(setOf(left), nobody, expanded = false))
    }

    @Test fun anItemThatIsOffOrOfATypeThisVersionDoesNotKnowIsNeverOwed() {
        for (expanded in listOf(false, true)) for (mode in HiddenMode.entries) {
            val ids = layout.copy(hiddenMode = mode).owedIds(Position.entries.toSet(), { true }, expanded)
            assertFalse("$mode $expanded: $ids", "o" in ids || "n" in ids)
        }
    }

    /** While presenting a strip draws only what presents and has something to say: that is what its area owes. */
    @Test fun whilePresentingAnOwedAreaOwesOnlyWhatItWouldDrawThen() {
        val presenting = layout.copy(presenting = true)
        assertEquals(emptySet<String>(), presenting.owedIds(setOf(left), nobody, expanded = true))
        assertEquals(setOf("t"), presenting.owedIds(setOf(left), { it.id == "t" }, expanded = false))
    }

    // ---- whether the carrier's ‹ stands for more than its own area -------------------------------------------

    @Test fun theCarriersChevronStandsForAnOwedItem() {
        assertTrue(layout.carrierOverflow(setOf(left), setOf("m"), nobody))
        assertFalse("an owed area with nothing to draw and nothing behind ‹ asks for no ‹", layout.carrierOverflow(setOf(middle), emptySet(), nobody))
        assertFalse(layout.carrierOverflow(emptySet(), emptySet(), nobody))
    }

    /** An owed area's ‹ is owed too: what waits behind it has no other ‹ to be reached by, drawn or not. */
    @Test fun theCarriersChevronStandsForWhatWaitsBehindAnOwedAreasChevron() {
        for (mode in listOf(HiddenMode.CLICK, HiddenMode.HOVER)) {
            val bar = layout.copy(hiddenMode = mode)
            assertTrue("$mode: Left's hidden items", bar.carrierOverflow(setOf(left), emptySet(), nobody))
            assertTrue("$mode: one waits though the other's rule brought it out", bar.carrierOverflow(setOf(left), setOf("t"), { it.id == "t" }))
            assertFalse("$mode: Left has its window, and its own ‹", bar.carrierOverflow(setOf(middle), emptySet(), nobody))
        }
        val onlyARule = layout.copy(items = listOf(clock, timerRule))
        assertTrue("a rule item waiting for its rule waits behind ‹", onlyARule.carrierOverflow(setOf(left), emptySet(), nobody))
        assertFalse("its rule holds: it is owed as a drawn item is, by its id", onlyARule.carrierOverflow(setOf(left), emptySet(), { true }))
        assertFalse("Show everything has nothing behind ‹", layout.copy(hiddenMode = HiddenMode.SHOW_ALL).carrierOverflow(setOf(left), emptySet(), nobody))
        assertFalse("an Off item and a type this version does not know are behind no ‹",
            layout.copy(items = listOf(clock, off, newer.copy(section = Section.HIDDEN))).carrierOverflow(setOf(left), emptySet(), nobody))
        assertFalse("while presenting nothing waits behind ‹", layout.copy(presenting = true).carrierOverflow(setOf(left), emptySet(), nobody))
    }

    /** Right has room and nothing hidden, Left has no room and holds the bar's only hidden items: the bar still has a ‹, and it reaches them. */
    @Test fun aPressOnTheChevronThatStandsForAnOwedAreaOpensTheMenuThatListsItsHiddenItems() {
        val bar = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(clock, battery, memory.copy(section = Section.HIDDEN)))
        val owed = setOf(left)
        for (expanded in listOf(false, true)) {
            val ids = bar.owedIds(owed, nobody, expanded)
            val carrier = bar.stripPlan(right, nobody, expanded, overflow = bar.carrierOverflow(owed, ids, nobody))
            assertTrue("expanded=$expanded: the first strip draws a ‹", carrier.chevron)
            assertFalse("which does not look open: none of what it stands for comes out beside it", carrier.open)
            val behind = bar.behindChevron(nobody)
            assertTrue("and a press on it opens the ‹ menu", chevronOpensMenu(bar.presenting, bar.hiddenMode, expanded,
                barHasHidden = behind.isNotEmpty(), areaHasHidden = behind.any { it.area == right }))
        }
        assertEquals("whose hidden list is the owed area's items", listOf("b", "m"), bar.notDrawn(nobody).map { it.id })
        // Without the owed ‹ the folded bar had none at all: nothing is owed by id while the hidden items are in.
        assertEquals(emptySet<String>(), bar.owedIds(owed, nobody, expanded = false))
        assertFalse(bar.stripPlan(right, nobody, expanded = false, overflow = false).chevron)
    }

    /** Presenting's ‹ is the home area's, and the only way to the menu that ends presenting: it moves to the carrier. */
    @Test fun presentingsChevronSurvivesItsHomeAreaHavingNoRoom() {
        // Right is not in use, so the Middle is home and Left, first in room order, is the carrier.
        val presenting = BarConfig(presenting = true, items = listOf(memory, date))
        assertEquals(middle, presenting.homeArea())
        assertTrue(presenting.carrierOverflow(setOf(middle), emptySet(), nobody))
        assertFalse("the home area has its window, and its own ‹", presenting.carrierOverflow(emptySet(), emptySet(), nobody))
        assertFalse("another area is owed, with nothing to draw", presenting.carrierOverflow(setOf(left), emptySet(), nobody))
        assertFalse("not presenting", presenting.copy(presenting = false).carrierOverflow(setOf(middle), emptySet(), nobody))
    }

    /** With every item in one area there is never an owed area: nothing is owed, and ‹ is for the strip's own items as it was. */
    @Test fun aLayoutInOneAreaOwesNothingAndItsChevronIsItsOwn() {
        for (area in Position.entries) for (presenting in listOf(false, true)) {
            val one = layout.copy(presenting = presenting, items = layout.items.map { it.copy(area = area) })
            val ids = one.owedIds(emptySet(), { true }, expanded = true)
            assertEquals(emptySet<String>(), ids)
            assertFalse("$area presenting=$presenting", one.carrierOverflow(emptySet(), ids) { true })
        }
    }
}

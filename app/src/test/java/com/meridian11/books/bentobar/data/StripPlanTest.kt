package com.meridian11.books.bentobar.data

import com.meridian11.books.bentobar.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What one area's strip and the settings preview show of a layout: the one function both ask. */
class StripPlanTest {
    private val clock = ItemConfig("c", "clock", Section.SHOWN)
    private val meeting = ItemConfig("e", "event", Section.SHOWN)
    private val memory = ItemConfig("m", "memory", Section.HIDDEN)
    private val timerRule = ItemConfig("t", "timer", Section.HIDDEN, whenActive = true)
    private val nobody = { _: ItemConfig -> false }
    private fun plan(c: BarConfig, area: Position, active: (ItemConfig) -> Boolean = nobody, expanded: Boolean = false, overflow: Boolean = false) =
        c.stripPlan(area, active, expanded, overflow)
    private fun ids(list: List<ItemConfig>) = list.map { it.id }
    private fun ItemConfig.at(area: Position, id: String = this.id) = copy(id = id, area = area)

    /** A layout with every item in one area, whichever it is, gets for that area what the one strip got. */
    private fun inEachArea(vararg items: ItemConfig, test: (items: List<ItemConfig>, area: Position) -> Unit) {
        for (area in Position.entries) test(items.map { it.at(area) }, area)
    }

    // ---- a layout in one area: the plan as it always was -------------------------------------------------

    @Test fun nextMeetingStaysAtTheFarLeft() = inEachArea(clock, meeting) { items, area ->
        // Its text changes width most, and at the outer edge that moves nothing else.
        assertEquals(listOf("e", "c"), ids(plan(BarConfig(items = items), area).visible))
    }

    @Test fun clickModeKeepsHiddenItemsBehindAChevronThatTheyKeepRoomFor() = inEachArea(clock, memory) { items, area ->
        val p = plan(BarConfig(hiddenMode = HiddenMode.CLICK, items = items), area)
        assertEquals(listOf("c"), ids(p.visible))
        assertEquals(listOf("m"), ids(p.hidden))
        assertEquals(emptyList<String>(), ids(p.revealed))
        assertTrue(p.chevron)
        assertTrue(p.chevronAlways)
        assertFalse(p.open)
    }

    @Test fun expandingRevealsWhatIsBehindTheChevronOnlyWhereThereIsOne() = inEachArea(clock, memory) { items, area ->
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, items = items)
        val p = plan(c, area, expanded = true)
        assertTrue(p.open)
        assertEquals(listOf("m"), ids(p.revealed))
        val showAll = plan(c.copy(hiddenMode = HiddenMode.SHOW_ALL), area, expanded = true)
        assertFalse("Show everything has no ‹ to fold them back with", showAll.open)
        assertEquals(emptyList<String>(), ids(showAll.revealed))
    }

    @Test fun anItemOutOnItsOwnIsNotAlsoBehindTheChevron() = inEachArea(timerRule, clock) { items, area ->
        val p = plan(BarConfig(hiddenMode = HiddenMode.CLICK, items = items), area, active = { it.id == "t" })
        assertEquals(listOf("t", "c"), ids(p.visible))
        assertEquals(emptyList<String>(), ids(p.hidden))
    }

    @Test fun overflowBringsTheChevronWithoutKeepingRoomForIt() = inEachArea(clock) { items, area ->
        val c = BarConfig(items = items)
        assertFalse(plan(c, area).chevron)
        val p = plan(c, area, overflow = true)
        assertTrue(p.chevron)
        assertFalse(p.chevronAlways)
    }

    @Test fun whilePresentingOnlyAPresentingTypeWithSomethingToSayShowsAndTheChevronStays() =
        inEachArea(clock, ItemConfig("t", "timer", Section.HIDDEN)) { items, area ->
            val c = BarConfig(hiddenMode = HiddenMode.CLICK, presenting = true, items = items)
            val idle = plan(c, area, expanded = true)
            assertEquals(emptyList<String>(), ids(idle.visible))
            assertEquals(emptyList<String>(), ids(idle.hidden))
            assertEquals(emptyList<String>(), ids(idle.revealed))
            assertFalse(idle.open)
            assertTrue(idle.chevron)
            assertTrue(idle.chevronAlways)
            assertEquals(listOf("t"), ids(plan(c, area, active = { it.id == "t" }).visible))
        }

    @Test fun anItemOfATypeThisVersionDoesNotKnowDrawsNothing() {
        val future = ItemConfig("f", "from-the-future", Section.SHOWN)
        inEachArea(future, future.copy(id = "g", section = Section.HIDDEN)) { items, area ->
            val p = plan(BarConfig(hiddenMode = HiddenMode.CLICK, items = items), area)
            assertEquals(emptyList<String>(), ids(p.visible))
            assertEquals(emptyList<String>(), ids(p.hidden))
        }
    }

    /** The plan as it was before items had areas, word for word: one strip, planned from the whole list. */
    private fun asBeforeAreas(c: BarConfig, active: (ItemConfig) -> Boolean, expanded: Boolean, overflow: Boolean): StripPlan {
        val visible = c.items.filter { c.shows(it, active(it)) }.sortedBy { if (it.type == Store.PINNED_LEFT) 0 else 1 }
        val hidden = if (c.presenting) emptyList() else c.behindChevron(active)
        return StripPlan(visible, hidden,
            open = expanded && c.hiddenMode != HiddenMode.SHOW_ALL && !c.presenting,
            chevron = c.presenting || overflow || hidden.isNotEmpty(),
            chevronAlways = c.presenting || hidden.isNotEmpty())
    }

    /**
     * Every layout whose items in use are in one area, in every state the plan reads: the home area's plan is the plan the
     * one strip had. Items that are Off or of an unknown type may be anywhere, since they are in no strip.
     */
    @Test fun aLayoutInOneAreaPlansItsHomeAreaExactlyAsTheOneStripWasPlanned() {
        val inUse = listOf(clock, meeting, memory, timerRule, ItemConfig("h", "timer", Section.HIDDEN), ItemConfig("b", "battery", Section.SHOWN))
        val notInUse = listOf(ItemConfig("o", "cpu", Section.OFF), ItemConfig("f", "from-the-future", Section.SHOWN))
        val actives = listOf<(ItemConfig) -> Boolean>(nobody, { true }, { it.type == "timer" })
        var plans = 0
        for (area in Position.entries) for (elsewhere in Position.entries) for (mask in 0 until (1 shl inUse.size)) for (rest in 0..notInUse.size) {
            val items = notInUse.take(rest).map { it.at(elsewhere) } + inUse.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.map { it.at(area) }
            if (items.isEmpty()) continue
            for (mode in HiddenMode.entries) for (presenting in listOf(false, true)) {
                val c = BarConfig(hiddenMode = mode, presenting = presenting, items = items)
                for (active in actives) for (expanded in listOf(false, true)) for (overflow in listOf(false, true)) {
                    assertEquals("$c expanded=$expanded overflow=$overflow", asBeforeAreas(c, active, expanded, overflow), c.stripPlan(c.homeArea(), active, expanded, overflow))
                    plans++
                }
            }
        }
        assertTrue("$plans plans compared", plans > 10_000)
    }

    // ---- a layout in more than one area: each area its own -----------------------------------------------

    private val right = Position.RIGHT
    private val middle = Position.CENTER
    private val left = Position.LEFT

    @Test fun eachAreaGetsOnlyItsOwnVisibleAndHiddenItems() {
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(
            clock.at(right), memory.at(left), clock.at(left, "c2"), memory.at(right, "m2"), clock.at(middle, "c3")))
        assertEquals(listOf("c"), ids(plan(c, right).visible))
        assertEquals(listOf("m2"), ids(plan(c, right).hidden))
        assertEquals(listOf("c2"), ids(plan(c, left).visible))
        assertEquals(listOf("m"), ids(plan(c, left).hidden))
        assertEquals(listOf("c3"), ids(plan(c, middle).visible))
        assertEquals(emptyList<String>(), ids(plan(c, middle).hidden))
        assertEquals("opened, an area brings out its own hidden items", listOf("m"), ids(plan(c, left, expanded = true).revealed))
    }

    @Test fun anAreaKeepsRoomForAChevronOnlyForItsOwnHiddenItems() {
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(clock.at(right), memory.at(right), clock.at(left, "c2")))
        assertTrue(plan(c, right).chevron)
        assertTrue(plan(c, right).chevronAlways)
        assertFalse("nothing of Left's is behind a ‹", plan(c, left).chevron)
        assertFalse(plan(c, left).chevronAlways)
        // The caller says what did not fit (the area's own, or for the first placed area anything that has no room at all).
        val full = plan(c, left, overflow = true)
        assertTrue(full.chevron)
        assertFalse(full.chevronAlways)
    }

    @Test fun anOpenBarShowsAChevronAsOpenOnlyWhereTheAreaHasHiddenItems() {
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(clock.at(right), memory.at(right), clock.at(left, "c2")))
        assertTrue(plan(c, right, expanded = true).open)
        // Left's ‹ can only stand for what does not fit: it has nothing to bring out, so it never looks open.
        val full = plan(c, left, expanded = true, overflow = true)
        assertTrue(full.chevron)
        assertFalse(full.open)
        assertEquals(emptyList<String>(), ids(full.revealed))
        assertFalse(plan(c, right).open)
    }

    /** As it always was in one strip: the bar is open, its last hidden item went away, and a ‹ still drawn shows as open. */
    @Test fun anOpenBarWithNoHiddenItemAtAllShowsEveryChevronAsOpen() {
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(clock.at(right), timerRule.at(right), clock.at(left, "c2")))
        val out = { item: ItemConfig -> item.id == "t" }
        for (area in listOf(right, left)) {
            assertTrue("$area", plan(c, area, active = out, expanded = true, overflow = true).open)
            assertFalse("$area folded", plan(c, area, active = out, overflow = true).open)
        }
        assertFalse("its rule ended: Right has a hidden item again and Left has none", plan(c, left, expanded = true, overflow = true).open)
        assertTrue(plan(c, right, expanded = true).open)
        assertFalse("Show everything has no ‹ to fold with, in any area",
            plan(c.copy(hiddenMode = HiddenMode.SHOW_ALL), left, active = out, expanded = true, overflow = true).open)
    }

    @Test fun whilePresentingOnlyTheHomeAreaHasTheChevronAndKeepsRoomForIt() {
        val timer = ItemConfig("t", "timer", Section.HIDDEN)
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, presenting = true, items = listOf(clock.at(right), timer.at(left), memory.at(left)))
        assertEquals(right, c.homeArea())
        assertTrue(plan(c, right).chevron)
        assertTrue(plan(c, right).chevronAlways)
        assertFalse(plan(c, left).chevron)
        assertFalse(plan(c, left).chevronAlways)
        assertFalse("an area nothing is in", plan(c, middle).chevron)
        // What presents shows in its own area, and nothing waits behind a ‹ anywhere.
        val running = { item: ItemConfig -> item.id == "t" }
        assertEquals(listOf("t"), ids(plan(c, left, active = running).visible))
        assertEquals(emptyList<String>(), ids(plan(c, right, active = running).visible))
        assertEquals(emptyList<String>(), ids(plan(c, left, expanded = true).hidden))
        assertFalse(plan(c, left, active = running).chevron)
        assertFalse("presenting's ‹ only opens the menu", plan(c, right, expanded = true).open)
        // An area whose own item did not fit still gets a ‹ for it, with no room kept.
        val full = plan(c, left, active = running, overflow = true)
        assertTrue(full.chevron)
        assertFalse(full.chevronAlways)
    }

    @Test fun presentingsChevronFollowsTheHomeAreaWhereRightIsNotInUse() {
        val c = BarConfig(presenting = true, items = listOf(clock.at(left), clock.at(middle, "c2"), clock.at(right, "c3").copy(section = Section.OFF)))
        assertEquals(middle, c.homeArea())
        assertTrue(plan(c, middle).chevron)
        assertTrue(plan(c, middle).chevronAlways)
        assertFalse(plan(c, left).chevron)
        assertFalse(plan(c, right).chevron)
    }

    @Test fun nextMeetingComesFirstInItsOwnAreaOnly() {
        val c = BarConfig(items = listOf(clock.at(right), clock.at(left, "c2"), meeting.at(left), meeting.at(right, "e2"), clock.at(middle, "c3")))
        assertEquals(listOf("e2", "c"), ids(plan(c, right).visible))
        assertEquals(listOf("e", "c2"), ids(plan(c, left).visible))
        assertEquals(listOf("c3"), ids(plan(c, middle).visible))
    }

    @Test fun anAreaWithNoItemPlansNothing() {
        val c = BarConfig(hiddenMode = HiddenMode.CLICK, items = listOf(clock.at(right), memory.at(right)))
        for (area in listOf(left, middle)) {
            val p = plan(c, area, expanded = true)
            assertEquals(emptyList<String>(), ids(p.visible))
            assertEquals(emptyList<String>(), ids(p.hidden))
            assertFalse(p.chevron)
            assertFalse(p.chevronAlways)
            assertFalse(p.open)
        }
    }

    // ---- what only reading the code can show ---------------------------------------------------------------

    @Test fun theBarAndThePreviewAskTheSameFunctionAndNeitherChoosesForItself() {
        val code = { name: String -> SourceScan.code(SourceScan.sources.single { it.name == name }) }
        for (name in listOf("BarService.kt", "BarPage.kt")) assertTrue(name, ".stripPlan(" in code(name))
        val preview = code("BarPage.kt")
        for (rule in listOf("sectionShows", "behindChevron", "sortedBy", "PINNED_LEFT")) assertFalse("BarPage.kt chooses by $rule", rule in preview)
        assertFalse("BarService.kt sorts the strip itself", "PINNED_LEFT" in code("BarService.kt"))
    }
}

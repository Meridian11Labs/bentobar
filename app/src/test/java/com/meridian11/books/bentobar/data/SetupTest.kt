package com.meridian11.books.bentobar.data

import com.meridian11.books.bentobar.items.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the library remembers of a removed item, and Remove from the bar. (That every type in the catalog makes a fresh
 * item that carries nothing is `SetupCatalogTest`, beside the Compose tests: one type's defaults are read from the app.)
 */
class SetupTest {
    private fun fresh(type: String, id: String = type) = Items.of(type)!!.fresh(id, Position.RIGHT)
    private val clock = fresh("clock")
    private val cpu = fresh("cpu")
    private val media = fresh("media")
    private val unknown = ItemConfig("u", "from-a-newer-version", options = mapOf("depth" to "7"))
    private fun BarConfig.at(id: String) = items.single { it.id == id }
    private fun layout(vararg items: ItemConfig, mode: HiddenMode = HiddenMode.SHOW_ALL) = BarConfig(items = items.toList(), hiddenMode = mode)

    @Test fun thePlusPutsATypeInTheBarAndAQuietOneUnderTheLineWithItsRuleOn() {
        assertEquals(ItemConfig("clock", "clock", Section.SHOWN, area = Position.RIGHT), clock)
        assertEquals(Section.HIDDEN, media.section)
        assertTrue(media.whenActive)
        assertEquals(Position.LEFT, Items.of("clock")!!.fresh("x", Position.LEFT).area)
        assertEquals(Items.of("shortcut")!!.defaultOptions(), fresh("shortcut").options)
    }

    @Test fun aFreshItemCarriesNothingAndAnOptionTheLookOrTheRuleEachMakeASetup() {
        for (item in listOf(clock, cpu, media, fresh("calendar"), fresh("shortcut"))) {
            assertFalse(item.type, item.carriesSetup())
            assertTrue(item.type, item.with("city", "Tokyo").carriesSetup())
            assertTrue(item.type, item.copy(display = Display.ICON).carriesSetup())
            assertTrue(item.type, item.copy(whenActive = !item.whenActive).carriesSetup())
        }
        assertTrue("a default taken away is a change too", fresh("shortcut").copy(options = emptyMap()).carriesSetup())
    }

    @Test fun whereAnItemStandsIsNoSetup() {
        assertFalse(clock.copy(section = Section.HIDDEN).carriesSetup())
        assertFalse(clock.copy(section = Section.OFF, area = Position.LEFT).carriesSetup())
        assertFalse(media.copy(section = Section.OFF).carriesSetup())
    }

    @Test fun anItemOfATypeThisVersionDoesNotKnowAlwaysCarriesOne() {
        assertTrue(unknown.carriesSetup())
        assertTrue(ItemConfig("u2", "from-a-newer-version").carriesSetup())
    }

    @Test fun aRemovedSetupIsKeptWhereItStoodAndAFreshItemLeavesTheLayout() {
        val tokyo = clock.with("city", "Tokyo")
        val c = layout(cpu, tokyo, fresh("calendar"))
        val kept = c.removed("clock")
        assertEquals(tokyo.copy(section = Section.OFF), kept.at("clock"))
        assertEquals(c.items.map { it.id }, kept.items.map { it.id })
        assertEquals(listOf("clock", "calendar"), c.removed("cpu").items.map { it.id })
    }

    @Test fun aRemovedItemOfAnUnknownTypeLeavesTheLayout() {
        // The library has no row to keep it under: Undo is the way back.
        assertEquals(listOf(clock), layout(clock, unknown).removed("u").items)
    }

    @Test fun aRuleLeftOnByAnItemThatIsAlwaysInTheBarIsNoSetup() {
        // Always, after Only when active, is show(): the bit stays. Such an item is as fresh as one without it.
        val left = cpu.copy(whenActive = true)
        assertEquals(Part.UPPER, layout(left).partOf(left))
        assertEquals(emptyList<ItemConfig>(), layout(left, clock).removed("cpu").items.filter { it.id == "cpu" })
        // With a setup of its own it is kept, and without the leftover.
        val kept = layout(left.with("above", "50"), clock).removed("cpu").at("cpu")
        assertEquals(Section.OFF, kept.section)
        assertFalse(kept.whenActive)
        // The same above the line in the default mode's Hidden, and with ‹ for an item that is Shown.
        assertFalse(layout(cpu.copy(section = Section.SHOWN, whenActive = true), mode = HiddenMode.CLICK).removed("cpu").items.any { it.id == "cpu" })
    }

    @Test fun aRuleAnItemWaitsOnIsItsSetup() {
        val waiting = cpu.copy(section = Section.HIDDEN, whenActive = true)
        for (mode in HiddenMode.entries) {
            val kept = layout(waiting, clock, mode = mode).removed("cpu").at("cpu")
            assertEquals(mode.name, Section.OFF, kept.section)
            assertTrue(mode.name, kept.whenActive)
        }
        // A quiet type that waits with its rule on is as the + made it; put in the bar for good, it is a setup.
        assertFalse(layout(media, clock).removed("media").items.any { it.id == "media" })
        val always = media.copy(section = Section.SHOWN, whenActive = false)
        assertEquals(always.copy(section = Section.OFF), layout(always, clock).removed("media").at("media"))
    }

    @Test fun aSetupComesBackWhereANewItemLandsInTheAreaInUseWithItsOptions() {
        val tokyo = clock.with("zone", "Asia/Tokyo").copy(section = Section.OFF, area = Position.LEFT, display = Display.TEXT)
        val c = layout(fresh("calendar"), cpu, tokyo)
        val back = c.broughtBack("clock")
        // Its options and its look as they were; the area is the one in use, not the one it was stored with.
        assertEquals(tokyo.copy(section = Section.SHOWN, area = Position.RIGHT), back.at("clock"))
        assertEquals(listOf("clock", "calendar", "cpu"), back.items.map { it.id })
        // After Next meeting, which keeps the far left.
        val meeting = fresh(Store.PINNED_LEFT, "e")
        assertEquals(listOf("e", "clock", "cpu"), layout(meeting, cpu, tokyo).broughtBack("clock").items.map { it.id })
        // An area the caller names.
        assertEquals(Position.CENTER, c.broughtBack("clock", Position.CENTER).at("clock").area)
    }

    @Test fun aSetupThatWaitedOnItsRuleComesBackUnderTheLine() {
        val waiting = cpu.copy(section = Section.OFF, whenActive = true)
        val back = layout(clock, waiting, fresh("calendar")).broughtBack("cpu")
        assertEquals(waiting.copy(section = Section.HIDDEN), back.at("cpu"))
        assertEquals(Part.LOWER, back.partOf(back.at("cpu")))
        assertEquals(listOf("clock", "calendar", "cpu"), back.items.map { it.id })
        // A quiet type that was always in the bar comes back above it.
        val always = media.copy(section = Section.OFF, whenActive = false)
        assertEquals(Section.SHOWN, layout(clock, always).broughtBack("media").at("media").section)
    }

    @Test fun onlyARememberedSetupIsBroughtBackAndUndoPutsItAwayAgain() {
        val tokyo = clock.with("zone", "Asia/Tokyo").copy(section = Section.OFF)
        val c = layout(cpu, tokyo, fresh("calendar"))
        assertSame(c, c.broughtBack("cpu"))
        assertSame(c, c.broughtBack("nobody"))
        val back = c.broughtBack("clock")
        assertEquals(c, back.undone(c, back))
        // Forget is the layout without it; Undo has it again, as it was and where it was.
        val forgot = c.without("clock")
        assertEquals(c, forgot.undone(c, forgot))
    }

    @Test fun removingWhatIsNotInTheBarChangesNothing() {
        val c = layout(clock, cpu.copy(section = Section.OFF, display = Display.ICON))
        assertSame(c, c.removed("cpu"))
        assertSame(c, c.removed("nobody"))
    }

    @Test fun removingTheLastItemLeavesTheEmptyBarWhereItWas() {
        val c = BarConfig(items = listOf(clock.copy(area = Position.LEFT)), position = Position.RIGHT)
        assertEquals(Position.LEFT, c.removed("clock").position)
    }

    @Test fun undoBringsBackTheExactLayout() {
        val layouts = listOf(
            layout(cpu, clock.with("city", "Tokyo"), fresh("calendar")),
            layout(cpu.copy(whenActive = true).with("above", "50"), clock),
            layout(clock, unknown),
            layout(media, clock, mode = HiddenMode.HOVER),
            BarConfig(items = listOf(clock.copy(area = Position.LEFT)), position = Position.RIGHT),
        )
        for (c in layouts) for (item in c.items) {
            val after = c.removed(item.id)
            assertEquals(item.id, c, after.undone(c, after))
        }
    }

    @Test fun undoKeepsWhatIsNotLayoutAndGivesUpOnALayoutArrangedOtherwiseSince() {
        val c = layout(cpu, clock, fresh("calendar"))
        val after = c.placed("clock", Position.RIGHT, Part.UPPER, 0)
        assertEquals(listOf("clock", "cpu", "calendar"), after.items.map { it.id })
        // An option changed after the move stays; the move is taken back.
        val later = after.copy(spacing = 20, items = after.items.map { if (it.id == "clock") it.with("city", "Tokyo") else it })
        val back = later.undone(c, after)!!
        assertEquals(c.items.map { it.id }, back.items.map { it.id })
        assertEquals("Tokyo", back.at("clock").options["city"])
        assertEquals(20, back.spacing)
        // The bar's own menu hid an item meanwhile: nothing is taken back.
        assertNull(after.hide("cpu").undone(c, after))
        assertNull(after.without("calendar").undone(c, after))
        // An item the change brought in goes again.
        val more = c.copy(items = c.items + fresh("timer"))
        assertEquals(c, more.undone(c, more))
    }

    @Test fun offItemsThatCarryNoSetupAreDroppedAndNothingElseIs() {
        val bare = clock.copy(id = "bare", section = Section.OFF)
        val setUp = clock.copy(id = "set", section = Section.OFF).with("city", "Tokyo")
        val c = layout(cpu, bare, setUp, unknown.copy(section = Section.OFF), media.copy(id = "quiet", section = Section.OFF))
        assertEquals(listOf("cpu", "set", "u"), c.withoutBareOff().items.map { it.id })
        val clean = layout(cpu, setUp)
        assertSame(clean, clean.withoutBareOff())
    }
}

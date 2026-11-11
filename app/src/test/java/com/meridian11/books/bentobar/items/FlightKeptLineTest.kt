package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Kept
import com.meridian11.books.bentobar.data.MapPrefs
import com.meridian11.books.bentobar.data.Online
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.carriesSetup
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.data.hide
import com.meridian11.books.bentobar.data.removed
import com.meridian11.books.bentobar.data.show
import com.meridian11.books.bentobar.data.without
import com.meridian11.books.bentobar.items.FlightRules.Tracked
import com.meridian11.books.bentobar.ui.Undo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The flight a Flight item follows is kept outside the layout, under the item's id. So the item is a setup whatever its
 * options say: Remove keeps it as a line in the library, with its flight, and the flight goes when the line is forgotten
 * and no Undo can bring it back.
 */
class FlightKeptLineTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "bentobar-flight-kept-${System.nanoTime()}")
    private val clock = ItemConfig("c", "clock")
    private val flight = FlightItem.fresh("f", Position.RIGHT)
    private fun at(id: String) = Store.config.value.items.single { it.id == id }
    private fun ids() = Store.config.value.items.map { it.id }
    private fun notes() = Kept.own("flight").names()
    private fun follow(id: String) = assertTrue(FlightLoad.take(id, Tracked("LH455", "2026-10-02"), 1_000))

    /** What the Flight item does on its own thread has been done. */
    private fun settled() {
        val done = CountDownLatch(1)
        Background.executor(FlightItem.type).execute { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    @Before fun fresh() {
        Online.init(dir)
        Store.open(MapPrefs()) { }
        Store.update { BarConfig(items = listOf(clock, flight)) }
        Undo.last.value = null
    }

    @After fun gone() {
        Undo.last.value = null
        settled()
        dir.deleteRecursively()
        Online.init(File(dir, "empty"))
    }

    @Test fun anItemThatFollowsAFlightIsASetupAndOneThatFollowsNoneIsNot() {
        assertFalse(flight.carriesSetup())
        follow("f")
        assertTrue(flight.carriesSetup())
        assertFalse("another Flight item follows nothing", flight.copy(id = "g").carriesSetup())
        FlightLoad.stop("f")
        assertFalse("Stop tracking: it is as the + made it again", flight.carriesSetup())
    }

    @Test fun removeKeepsItAsALineWithItsFlightAndALaterChangeLeavesItAlone() {
        follow("f")
        // The settings' Remove and the one in the bar's own menu are this one step.
        Store.update { it.removed("f") }
        assertEquals(Section.OFF, at("f").section)
        // Every change drops the Off items that carry nothing (what 1.8 could leave): not this one.
        Store.update { it.hide("c") }
        assertEquals(listOf("c", "f"), ids())
        assertEquals(setOf("f"), notes())
        // One that follows nothing leaves the layout, as any item the + made.
        Store.update { c -> c.copy(items = c.items + flight.copy(id = "g")) }
        Store.update { it.removed("g") }
        assertEquals(listOf("c", "f"), ids())
    }

    @Test fun theFlightStaysWhileTheLineDoesOrUndoCanBringItBackAndGoesWhenTheLineIsForgottenForGood() {
        follow("f")
        follow("g")
        Store.update { c -> c.copy(items = c.items + flight.copy(id = "g")).removed("f") }
        // Another Flight item is read, which lets go of what belongs to no item: the line's flight is not that.
        FlightLoad.kept("g", 2_000, FlightItem.ids())
        assertEquals(setOf("f", "g"), notes())
        // Forget, which Undo can take back for as long as nothing else changes.
        assertTrue(Undo.change { it.without("f") })
        FlightLoad.kept("g", 2_000, FlightItem.ids())
        settled()
        assertEquals(setOf("f", "g"), notes())
        assertTrue(Undo.undo())
        assertEquals("the line is back, with its flight", Section.OFF, at("f").section)
        assertTrue(at("f").carriesSetup())
        // Forgotten again, and then something else changes: no Undo brings it back, and its flight goes.
        assertTrue(Undo.change { it.without("f") })
        assertTrue(Undo.change { it.hide("c") })
        settled()
        assertEquals(setOf("g"), notes())
        // The last Flight item goes the same way, and nothing of a flight stays.
        assertTrue(Undo.change(removed = "g") { it.removed("g") })
        assertEquals(Section.OFF, at("g").section)
        assertTrue(Undo.change { it.without("g") })
        assertTrue(Undo.change { it.show("c") })
        settled()
        assertEquals(emptySet<String>(), notes())
        assertEquals(listOf("c"), ids())
    }
}

package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.MapPrefs
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.hide
import com.meridian11.books.bentobar.data.moved
import com.meridian11.books.bentobar.data.removed
import com.meridian11.books.bentobar.data.show
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.items.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Bar page's one Undo: the last layout change, taken back once, and never over a layout arranged otherwise since. */
class UndoTest {
    private val a = ItemConfig("a", "nope")
    private val b = ItemConfig("b", "nope", options = mapOf("k" to "1"))
    private val c = ItemConfig("c", "nope", Section.HIDDEN)
    private val layout = BarConfig(items = listOf(a, b, c))
    private fun ids() = Store.config.value.items.map { it.id }

    @Before fun fresh() {
        Store.open(MapPrefs()) { }
        Store.update { layout }
        Undo.last.value = null
    }

    @Test fun aChangeIsTakenBackOnceAndASecondUndoDoesNothing() {
        assertTrue(Undo.change { it.copy(items = it.items.moved("a", Section.SHOWN, 1)) })
        assertEquals(listOf("b", "a", "c"), ids())
        assertTrue(Undo.undo())
        assertEquals(layout, Store.config.value)
        assertFalse(Undo.undo())
        assertEquals(layout, Store.config.value)
    }

    @Test fun aChangeThatChangesNothingIsNotKeptAndTheOneBeforeItStaysToBeUndone() {
        assertTrue(Undo.change { it.hide("a") })
        assertFalse(Undo.change { it.hide("a") })
        assertTrue(Undo.undo())
        assertEquals(layout, Store.config.value)
    }

    @Test fun aRemovedItemComesBackWhereItWasWhetherItWasForgottenOrKeptAsASetup() {
        // As the + made it: it leaves the layout. With a setup of its own (and a type this version doesn't know): kept, out of the bar.
        val plain = Items.of("memory")!!.fresh("m", Position.RIGHT)
        val four = layout.copy(items = listOf(a, plain, b, c))
        for (id in listOf("m", "b")) {
            Store.update { four }
            assertTrue(Undo.change(removed = id) { it.removed(id) })
            assertTrue(Store.config.value.items.none { it.id == id && it.section != Section.OFF })
            assertEquals(id, Undo.last.value?.removed)
            assertTrue(Undo.undo())
            assertEquals(four, Store.config.value)
        }
        assertEquals("the plain one was gone, not kept", listOf("a", "b", "c"), four.removed("m").items.map { it.id })
    }

    @Test fun anOptionChangedSinceIsKeptByTheUndo() {
        Undo.change { it.hide("a") }
        Store.updateItem("b") { it.copy(options = mapOf("k" to "2")) }
        assertTrue(Undo.undo())
        assertEquals(Section.SHOWN, Store.config.value.items.first { it.id == "a" }.section)
        assertEquals("2", Store.config.value.items.first { it.id == "b" }.options["k"])
    }

    @Test fun aLayoutArrangedOtherwiseSinceIsLeftAloneAndTheChangeIsDropped() {
        Undo.change(removed = "b") { it.removed("b") }
        // The bar's own menu, with the settings window open.
        Store.update { it.show("c") }
        val now = Store.config.value
        assertFalse(Undo.offered(Undo.last.value!!, now))
        assertFalse(Undo.undo())
        assertEquals(now, Store.config.value)
        assertNull(Undo.last.value)
    }

    @Test fun whatTheStoreDidItselfIsNotedAndTakenBack() {
        val before = Store.config.value
        Store.update { it.copy(items = it.items + ItemConfig("d", "nope")) }
        assertTrue(Undo.note(before))
        assertTrue(Undo.undo())
        assertEquals(layout, Store.config.value)
        assertFalse("nothing happened since: nothing is noted", Undo.note(Store.config.value))
    }
}

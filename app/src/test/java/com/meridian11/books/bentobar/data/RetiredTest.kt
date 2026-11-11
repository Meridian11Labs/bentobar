package com.meridian11.books.bentobar.data

import com.meridian11.books.bentobar.items.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A layout that holds an item of a retired type is read without it: no row in Settings, nothing in the bar, no error. */
class RetiredTest {
    private fun read(text: String): LayoutRead.Ok = readLayout(text) as LayoutRead.Ok

    @Test fun noRetiredTypeIsRegistered() {
        for (type in Retired.types) assertNull(type, Items.of(type))
    }

    @Test fun aLayoutWithACryptoItemLoadsWithoutItAndIsSavedAgain() {
        // As a 2.0 test build wrote it: a Crypto item between two others, with its coins and currency.
        val text = """{"version":4,"items":[
            {"id":"a1","type":"clock","area":"RIGHT"},
            {"id":"k1","type":"crypto","section":"SHOWN","area":"LEFT","options":{"coins":"BTC ETH","quote":"EUR"}},
            {"id":"b2","type":"cpu","section":"HIDDEN","area":"RIGHT"}]}"""
        val read = read(text)
        assertEquals(listOf("a1", "b2"), read.config.items.map { it.id })
        assertTrue("so the stored layout is written again without it", read.changed)
        val again = readLayout(layoutJson.encodeToString(BarConfig.serializer(), read.config))
        assertEquals(LayoutRead.Ok(read.config, changed = false), again)
    }

    @Test fun aLayoutWithNothingRetiredIsTheSameLayout() {
        val c = BarConfig(items = listOf(ItemConfig("a1", "clock"), ItemConfig("u1", "fromANewerVersion", options = mapOf("depth" to "7"))))
        assertTrue(c.withoutRetired() === c)
        // An unknown type that is not retired may be from a newer version: it stays, options and all.
        assertEquals(c.items, read(layoutJson.encodeToString(BarConfig.serializer(), c)).config.items)
    }

    @Test fun aLayoutOfOnlyRetiredItemsIsAnEmptyLayoutNotNoLayout() {
        val read = read("""{"version":4,"items":[{"id":"k1","type":"crypto"}]}""")
        assertEquals(emptyList<ItemConfig>(), read.config.items)
        assertFalse(read.config.items.any { it.type in Retired.types })
    }
}

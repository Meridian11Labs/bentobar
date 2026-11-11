package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.SourceScan
import com.meridian11.books.bentobar.SourceScan.place
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Kept
import com.meridian11.books.bentobar.data.MapPrefs
import com.meridian11.books.bentobar.data.Online
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.carriesSetup
import com.meridian11.books.bentobar.data.hide
import com.meridian11.books.bentobar.data.removed
import com.meridian11.books.bentobar.data.without
import com.meridian11.books.bentobar.ui.Undo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A prompter's script and what is on a shelf are the user's own words: they are kept in memory and in the item's own
 * file in the no-backup directory (`Kept.own`), never in the layout (which Copy settings and a backup carry), never in
 * a log line or a debug hook's output. Other private texts have their own tests, beside their items.
 */
class PrivateTextTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "bentobar-private-${System.nanoTime()}")
    private val prefs = MapPrefs()
    private val clock = ItemConfig("c", "clock")
    private val prompter = ItemConfig("p", PrompterItem.type)
    private val shelf = ItemConfig("s", ShelfItem.type)
    private val script = "Good morning, everyone. The figures for the quarter came in on Friday, and they are better than we hoped.\n" +
        "First the numbers, then what they mean for the plan."
    private val entries = listOf("The door code is 4711 and the meeting room is the one on the left", "https://a.example/share?token=abc123")

    private fun at(id: String) = Store.config.value.items.single { it.id == id }

    /** What a type does on its own thread has been done. */
    private fun settled(type: String) {
        val done = CountDownLatch(1)
        Background.executor(type).execute { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    /** Nothing of [text] is in [item]'s options, nor in the layout as it is stored. */
    private fun notInTheLayout(item: ItemConfig, text: String) {
        for ((key, value) in item.options) {
            assertTrue("$key holds ${value.length} characters", value.length <= 64)
            assertFalse(key, value == text || text in value)
        }
        assertFalse("the stored layout holds it", prefs.map.values.any { it is String && text in it })
    }

    @Before fun fresh() {
        Online.init(dir)
        Store.open(prefs) { }
        Store.update { BarConfig(items = listOf(clock, prompter, shelf)) }
        Undo.last.value = null
    }

    @After fun gone() {
        Undo.last.value = null
        PrompterItem.forgetOwn("p")
        ShelfItem.forgetOwn("s")
        settled(PrompterItem.type)
        settled(ShelfItem.type)
        dir.deleteRecursively()
        Online.init(File(dir, "empty"))
    }

    @Test fun aPrompterScriptIsNotInTheLayout() {
        PrompterItem.edit("p", script)
        PrompterItem.save("p")
        // A change of its own options afterwards (the speed) does not carry it in either.
        Store.updateItem("p") { PrompterRules.speed.set(it, 7) }
        settled(PrompterItem.type)
        notInTheLayout(at("p"), script)
        assertEquals("kept in its own file", script, Kept.own("prompter").read("p")?.text)
        assertEquals("and in memory", script, PrompterItem.script("p"))
        assertTrue("so Remove keeps the item as a line", at("p").carriesSetup())
    }

    @Test fun aBlankScriptKeepsNothing() {
        PrompterItem.edit("p", script)
        PrompterItem.save("p")
        PrompterItem.edit("p", "  \n ")
        PrompterItem.save("p")
        settled(PrompterItem.type)
        assertNull(Kept.own("prompter").read("p"))
        assertFalse(at("p").carriesSetup())
    }

    @Test fun shelfEntriesAreNotInTheLayout() {
        ShelfItem.add("s", entries)
        settled(ShelfItem.type)
        for (text in entries) notInTheLayout(at("s"), text)
        val kept = Kept.own("shelf").read("s")?.text
        assertEquals(entries, kept?.let(ShelfRules::decode)?.map { it.text })
        assertEquals(entries, ShelfItem.held("s")?.map { it.text })
        assertTrue(at("s").carriesSetup())
        // Cleared, it holds nothing on the device and is as the + made it.
        ShelfItem.clear("s")
        settled(ShelfItem.type)
        assertNull(Kept.own("shelf").read("s"))
        assertEquals(emptyList<ShelfRules.Entry>(), ShelfItem.held("s"))
        assertFalse(at("s").carriesSetup())
    }

    @Test fun noLogLineInFilesThatHoldPrivateText() {
        val files = SourceScan.sources.filter { f -> f.place().let { it.startsWith("items/Prompter") || it.startsWith("items/Shelf") } }
        assertEquals(files.map { it.name }.toString(), 6, files.size)
        for (f in files) {
            val code = SourceScan.code(f)
            for (call in listOf("Log.", "println(", "printStackTrace", "System.out", "System.err")) assertFalse("${f.name} calls $call", call in code)
        }
    }

    @Test fun noDebugLineSaysTheText() {
        PrompterItem.edit("p", script)
        ShelfItem.add("s", entries)
        settled(ShelfItem.type)
        for (line in listOfNotNull(PrompterItem.debug(emptyList()), ShelfItem.debug(emptyList()))) {
            assertFalse(line, script.take(20) in line)
            for (text in entries) assertFalse(line, text.take(20) in line)
        }
        // Nor what a type is asked to say about its item in the bar, which the debug hooks print too.
        assertTrue("both types are discreet", PrompterItem.discreet && ShelfItem.discreet)
    }

    @Test fun whatIsKeptForAnItemThatIsGoneIsSweptAtTheStart() {
        PrompterItem.edit("p", script)
        PrompterItem.save("p")
        ShelfItem.add("s", entries)
        // Left behind by items that went while the process ended (Undo is in memory, so it never let them go).
        Kept.own("prompter").write("gone", "an old script", 1)
        Kept.own("shelf").write("gone", ShelfRules.encode(ShelfRules.take(emptyList(), listOf("an old note"))), 1)
        settled(PrompterItem.type)
        settled(ShelfItem.type)
        PrompterItem.settle(Settle.SERVICE_STARTED)
        ShelfItem.settle(Settle.SERVICE_STARTED)
        settled(PrompterItem.type)
        settled(ShelfItem.type)
        assertEquals(setOf("p"), Kept.own("prompter").names())
        assertEquals(setOf("s"), Kept.own("shelf").names())
    }

    @Test fun aLineKeepsItsTextAndAForgottenLineTakesItWithIt() {
        PrompterItem.edit("p", script)
        PrompterItem.save("p")
        ShelfItem.add("s", entries)
        settled(PrompterItem.type)
        settled(ShelfItem.type)
        // Removed, each waits in the library as a line, with its text.
        assertTrue(Undo.change(removed = "p") { it.removed("p") })
        assertTrue(Undo.change(removed = "s") { it.removed("s") })
        assertEquals(Section.OFF, at("p").section)
        assertEquals(Section.OFF, at("s").section)
        // The bar's start keeps what a line holds.
        PrompterItem.settle(Settle.SERVICE_STARTED)
        ShelfItem.settle(Settle.SERVICE_STARTED)
        settled(PrompterItem.type)
        settled(ShelfItem.type)
        assertEquals(setOf("p"), Kept.own("prompter").names())
        assertEquals(setOf("s"), Kept.own("shelf").names())
        // Forgotten, and then something else changes: no Undo brings them back, and their texts go, from memory too.
        assertTrue(Undo.change { it.without("p").without("s") })
        assertTrue(Undo.change { it.hide("c") })
        settled(PrompterItem.type)
        settled(ShelfItem.type)
        assertEquals(emptySet<String>(), Kept.own("prompter").names())
        assertEquals(emptySet<String>(), Kept.own("shelf").names())
        assertNull(PrompterItem.script("p"))
        assertNull(ShelfItem.held("s"))
    }
}

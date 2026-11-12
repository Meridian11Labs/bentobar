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
 * A prompter's script is the user's own words: it is kept in memory and in the item's own
 * file in the no-backup directory (`Kept.own`), never in the layout (which Copy settings and a backup carry), never in
 * a log line or a debug hook's output. Other private texts have their own tests, beside their items.
 */
class PrivateTextTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "bentobar-private-${System.nanoTime()}")
    private val prefs = MapPrefs()
    private val clock = ItemConfig("c", "clock")
    private val prompter = ItemConfig("p", PrompterItem.type)
    private val script = "Good morning, everyone. The figures for the quarter came in on Friday, and they are better than we hoped.\n" +
        "First the numbers, then what they mean for the plan."

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
        Store.update { BarConfig(items = listOf(clock, prompter)) }
        Undo.last.value = null
    }

    @After fun gone() {
        Undo.last.value = null
        PrompterItem.forgetOwn("p")
        settled(PrompterItem.type)
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

    @Test fun noLogLineInFilesThatHoldPrivateText() {
        val files = SourceScan.sources.filter { f -> f.place().let { it.startsWith("items/Prompter") } }
        assertEquals(files.map { it.name }.toString(), 3, files.size)
        for (f in files) {
            val code = SourceScan.code(f)
            for (call in listOf("Log.", "println(", "printStackTrace", "System.out", "System.err")) assertFalse("${f.name} calls $call", call in code)
        }
    }

    @Test fun noDebugLineSaysTheText() {
        PrompterItem.edit("p", script)
        for (line in listOfNotNull(PrompterItem.debug(emptyList()))) assertFalse(line, script.take(20) in line)
        // Nor what the type is asked to say about its item in the bar, which the debug hooks print too.
        assertTrue("the type is discreet", PrompterItem.discreet)
    }

    @Test fun whatIsKeptForAnItemThatIsGoneIsSweptAtTheStart() {
        PrompterItem.edit("p", script)
        PrompterItem.save("p")
        // Left behind by items that went while the process ended (Undo is in memory, so it never let them go).
        Kept.own("prompter").write("gone", "an old script", 1)
        settled(PrompterItem.type)
        PrompterItem.settle(Settle.SERVICE_STARTED)
        settled(PrompterItem.type)
        assertEquals(setOf("p"), Kept.own("prompter").names())
    }

    @Test fun aLineKeepsItsTextAndAForgottenLineTakesItWithIt() {
        PrompterItem.edit("p", script)
        PrompterItem.save("p")
        settled(PrompterItem.type)
        // Removed, it waits in the library as a line, with its text.
        assertTrue(Undo.change(removed = "p") { it.removed("p") })
        assertEquals(Section.OFF, at("p").section)
        // The bar's start keeps what a line holds.
        PrompterItem.settle(Settle.SERVICE_STARTED)
        settled(PrompterItem.type)
        assertEquals(setOf("p"), Kept.own("prompter").names())
        // Forgotten, and then something else changes: no Undo brings it back, and its text goes, from memory too.
        assertTrue(Undo.change { it.without("p") })
        assertTrue(Undo.change { it.hide("c") })
        settled(PrompterItem.type)
        assertEquals(emptySet<String>(), Kept.own("prompter").names())
        assertNull(PrompterItem.script("p"))
    }
}

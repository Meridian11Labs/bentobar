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
import com.meridian11.books.bentobar.items.WebValueRules.Reading
import com.meridian11.books.bentobar.net.Http
import com.meridian11.books.bentobar.net.HttpTransport
import com.meridian11.books.bentobar.net.OwnAddress
import com.meridian11.books.bentobar.net.OwnTransport
import com.meridian11.books.bentobar.net.Reply
import com.meridian11.books.bentobar.net.Why
import com.meridian11.books.bentobar.ui.Undo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Value from a URL: the address is the user's own text and may hold a token, so it is kept in memory and in the item's
 * own file in the no-backup directory (`Kept.own`), never in the layout (which Copy settings and a backup carry), never in
 * a log line or a debug hook's output; and of a reply only the value it holds is kept.
 */
class WebValueLoadTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "bentobar-webvalue-${System.nanoTime()}")
    private val prefs = MapPrefs()
    private val clock = ItemConfig("c", "clock")
    private val item = ItemConfig("w", WebValueItem.type)
    private val address = "https://home.example:8443/v1/temp?token=abc123"
    private val now = 1_776_000_000_000L
    private val asked = ArrayList<OwnAddress>()
    private var answer: () -> Reply = { Reply.Ok("""{"temp":21.46,"token":"abc123","place":"home.example"}""") }

    private fun at(id: String) = Store.config.value.items.single { it.id == id }

    /** What the type does on its own thread has been done. */
    private fun settled() {
        val done = CountDownLatch(1)
        Background.executor(WebValueItem.type).execute { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    /** Nothing of [text] is in [item]'s options, nor in the layout as it is stored. */
    private fun notInTheLayout(item: ItemConfig, text: String) {
        for ((key, value) in item.options) for (part in listOf(text, "abc123", "home.example")) assertFalse("$key holds it", part in value)
        assertFalse("the stored layout holds it", prefs.map.values.any { v -> v is String && listOf(text, "abc123", "home.example").any { it in v } })
    }

    @Before fun fresh() {
        Online.init(dir)
        Store.open(prefs) { }
        Store.update { BarConfig(items = listOf(clock, item)) }
        Undo.last.value = null
        Http.ownTransport = OwnTransport { asked += it; answer() }
        Http.ownAllowed = { true }
        Http.connected = { true }
        Http.onMain = { false }
        Http.resetCounts()
    }

    @After fun gone() {
        Undo.last.value = null
        WebValueItem.forgetOwn("w")
        WebValueItem.debug(listOf("off"))
        settled()
        Http.ownTransport = HttpTransport()
        Http.ownAllowed = { false }
        Http.resetCounts()
        dir.deleteRecursively()
        Online.init(File(dir, "empty"))
    }

    @Test fun theAddressIsNotInTheLayout() {
        WebValueItem.edit("w", address)
        WebValueItem.save("w")
        // A change of its own options afterwards (its label, the path) does not carry it in either.
        Store.updateItem("w") { WebValueRules.path.set(WebValueRules.label.set(it, "Room"), "temp") }
        settled()
        notInTheLayout(at("w"), address)
        assertEquals("kept in its own file", address, Kept.own("webvalue").read("w")?.text)
        assertEquals("and in memory", address, WebValueItem.addresses.value["w"])
        assertTrue("so Remove keeps the item as a line", at("w").carriesSetup())
        // Nor is it among the options a type declares, which a layout carries.
        assertEquals(emptyMap<String, String>(), WebValueItem.defaultOptions())
    }

    @Test fun aBlankAddressKeepsNothing() {
        WebValueItem.edit("w", address)
        WebValueItem.save("w")
        WebValueItem.edit("w", "  ")
        WebValueItem.save("w")
        settled()
        assertNull(Kept.own("webvalue").read("w"))
        assertFalse(at("w").carriesSetup())
    }

    @Test fun noLogLineInWebValueFiles() {
        val files = SourceScan.sources.filter { it.place().startsWith("items/WebValue") }
        assertEquals(files.map { it.name }.toString(), 3, files.size)
        for (f in files) {
            val code = SourceScan.code(f)
            for (call in listOf("Log.", "println(", "printStackTrace", "System.out", "System.err")) assertFalse("${f.name} calls $call", call in code)
        }
    }

    @Test fun theDebugLinePrintsNoAddressAndNoHost() {
        WebValueItem.edit("w", address)
        WebValueItem.save("w")
        settled()
        val reading = WebValueLoad.load(WebValueItem.addressOf("w"), "temp", null, now)!!
        val lines = listOfNotNull(WebValueItem.debug(emptyList()), WebValueItem.debug(listOf("stage", "value")), WebValueItem.debug(listOf("off")),
            reading.toString(), WebValueItem.addressOf("w").toString())
        for (line in lines) for (secret in listOf("home.example", "8443", "token", "abc123", "21.46"))
            assertFalse("$line says $secret", secret in line)
        // What the debug hook prints of an item's state is its text's length: the type is discreet.
        assertTrue(WebValueItem.discreet)
    }

    @Test fun aGoodReplyIsItsValueAndNothingElseOfIt() {
        val r = WebValueLoad.load(OwnAddress.parse(address), "temp", null, now)!!
        assertEquals(Reading("21.46", "temp", now), r)
        assertEquals(1, asked.size)
        assertEquals(1, Http.sentOwn())
        // A plain text's first line, with no path.
        answer = { Reply.Ok("open\nsince 9:00\n") }
        assertEquals("open", WebValueLoad.load(OwnAddress.parse(address), "", r, now)!!.value)
    }

    @Test fun aReplyWithoutTheValueIsNoValueNotAFailure() {
        val r = WebValueLoad.load(OwnAddress.parse(address), "humidity", null, now)!!
        assertNull(r.value)
        assertNull(r.failure)
        assertEquals(0, r.misses)
        answer = { Reply.Ok("<!doctype html><html>Sign in to this network</html>") }
        assertNull(WebValueLoad.load(OwnAddress.parse(address), "temp", null, now)!!.value)
    }

    @Test fun aFailureKeepsTheValueThereWasAndCountsAMiss() {
        val good = WebValueLoad.load(OwnAddress.parse(address), "temp", null, now)!!
        answer = { Reply.Failed(Why.OFFLINE) }
        val once = WebValueLoad.load(OwnAddress.parse(address), "temp", good, now + 60_000)!!
        assertEquals("21.46", once.value)
        assertEquals(Failure.OFFLINE, once.failure)
        assertEquals(1, once.misses)
        assertEquals(now, once.fetchedAt)
        answer = { Reply.Failed(Why.STATUS, 429, retryAfterSec = 120) }
        val slow = WebValueLoad.load(OwnAddress.parse(address), "temp", once, now + 120_000)!!
        assertEquals(Failure.SLOW_DOWN, slow.failure)
        assertEquals(30 * 60_000L, WebValueRules.every(slow, 5))
        // Meanwhile the next ask is held, and says what started the hold, with nothing sent.
        val held = WebValueLoad.load(OwnAddress.parse(address), "temp", slow, now + 130_000)!!
        assertEquals(Failure.SLOW_DOWN, held.failure)
        assertEquals(slow.misses, held.misses)
        assertEquals(3, asked.size)
        // A redirect is an answer: no answer, never followed.
        Http.resetCounts()
        answer = { Reply.Failed(Why.STATUS, 302) }
        assertEquals(Failure.NO_ANSWER, WebValueLoad.load(OwnAddress.parse(address), "temp", null, now)!!.failure)
    }

    @Test fun anAddressThatIsNotOneIsNeverAsked() {
        for (text in listOf("http://home.example/v", "https://me:pw@home.example/v", "home.example/v", "")) {
            WebValueItem.edit("w", text)
            assertNull(text, WebValueItem.addressOf("w"))
            assertNull(WebValueLoad.load(WebValueItem.addressOf("w"), "temp", null, now))
        }
        assertEquals(emptyList<OwnAddress>(), asked)
        WebValueItem.edit("w", " $address ")
        assertNotNull("spaces around a pasted address are not part of it", WebValueItem.addressOf("w"))
    }

    @Test fun whileTheGateIsShutNothingIsAskedAndNothingIsSaid() {
        Http.ownAllowed = { false }
        assertNull(WebValueLoad.load(OwnAddress.parse(address), "temp", null, now))
        Http.ownAllowed = { true }
        Http.connected = { false }
        val offline = WebValueLoad.load(OwnAddress.parse(address), "temp", null, now)!!
        assertEquals(Failure.OFFLINE, offline.failure)
        assertEquals("nothing went out, so no miss", 0, offline.misses)
        assertEquals(emptyList<OwnAddress>(), asked)
        assertEquals(0, Http.sentOwn())
    }

    @Test fun whatIsKeptForAnItemThatIsGoneIsSweptAtTheStart() {
        WebValueItem.edit("w", address)
        WebValueItem.save("w")
        // Left behind by an item that went while the process ended (Undo is in memory, so it never let it go).
        Kept.own("webvalue").write("gone", "https://old.example/?token=old", 1)
        settled()
        WebValueItem.settle(Settle.SERVICE_STARTED)
        settled()
        assertEquals(setOf("w"), Kept.own("webvalue").names())
        // The bar hiding, the service ending, the item leaving: none of them deletes a line's address.
        WebValueItem.settle(Settle.SERVICE_ENDING)
        WebValueItem.settle(Settle.LEFT_LAYOUT)
        settled()
        assertEquals(setOf("w"), Kept.own("webvalue").names())
    }

    @Test fun aLineKeepsItsAddressAndAForgottenLineTakesItWithIt() {
        WebValueItem.edit("w", address)
        WebValueItem.save("w")
        settled()
        assertTrue(Undo.change(removed = "w") { it.removed("w") })
        assertEquals(Section.OFF, at("w").section)
        WebValueItem.settle(Settle.SERVICE_STARTED)
        settled()
        assertEquals(setOf("w"), Kept.own("webvalue").names())
        // Forgotten, and then something else changes: no Undo brings it back, and its address goes, from memory too.
        assertTrue(Undo.change { it.without("w") })
        assertTrue(Undo.change { it.hide("c") })
        settled()
        assertEquals(emptySet<String>(), Kept.own("webvalue").names())
        assertNull(WebValueItem.addresses.value["w"])
    }
}

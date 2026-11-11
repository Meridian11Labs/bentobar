package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.WebValueRules.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebValueRulesTest {
    private val json = """{"a":{"b":[{"c":12.5}]},"ok":true,"s":"hi","n":null,"list":[1,2],"t":"two\nlines"}"""

    @Test fun aPlainTextReplyIsItsFirstLine() {
        assertEquals("21.5", WebValueRules.read("21.5\n", ""))
        assertEquals("open", WebValueRules.read("\n  \r\n  open  \nclosed\n", ""))
        assertNull(WebValueRules.read("   \n\n", ""))
        assertNull(WebValueRules.read("", ""))
        // Kept as one line, with nothing that turns the bar's text around.
        assertEquals("up", WebValueRules.read("\u202Eup", ""))
    }

    @Test fun aJsonPathFindsNumbersTextAndBooleans() {
        val body = """{"a":{"b":[{"c":12.5}]},"ok":true,"s":"hi"}"""
        assertEquals("12.5", WebValueRules.read(body, "a.b[0].c"))
        assertEquals("true", WebValueRules.read(body, "ok"))
        assertEquals("hi", WebValueRules.read(body, "s"))
        assertEquals("12.5", WebValueRules.read(body, " a.b[0].c "))
        // A list at the top, and an index after an index.
        assertEquals("7", WebValueRules.read("""[{"v":[[5,7]]}]""", "[0].v[0][1]"))
        // A text of several lines is one line in the bar.
        assertEquals("two lines", WebValueRules.read(json, "t"))
    }

    @Test fun aPathThatIsNotThereIsNoValue() {
        for (path in listOf("a.x", "a.b[3].c", "a", "a.b", "n", "list", "s[0]", "ok.x", "a..b", "a.", ".a", "a[x]", "a[-1]", "[", "a]"))
            assertNull(path, WebValueRules.read(json, path))
    }

    @Test fun htmlIsNoValue() {
        val page = "<!doctype html>\n<html><body>Sign in to this network</body></html>"
        assertNull(WebValueRules.read(page, "a.b"))
        assertNull("a page is no line of text either", WebValueRules.read(page, ""))
        assertNull(WebValueRules.read("{\"a\": ", "a"))
        assertNull(WebValueRules.read("[".repeat(100_000), "a"))
    }

    @Test fun aLongValueIsCut() {
        assertEquals("x".repeat(24), WebValueRules.shown("x".repeat(40), "", ""))
        assertEquals("Room 21.46 °C", WebValueRules.shown("21.4567", "Room", "°C"))
        assertEquals("21.5", WebValueRules.shown("21.5", "", ""))
        assertEquals("-3.14", WebValueRules.shown("-3.14159", " ", ""))
        assertEquals("Up true", WebValueRules.shown("true", "Up", ""))
        // Only a plain number is rounded: a version or a date is text.
        assertEquals("1.2.345", WebValueRules.shown("1.2.345", "", ""))
        assertEquals(WebValueRules.VALUE_CHARS, 24)
        // What is kept of a value at all is short too.
        assertEquals(WebValueRules.KEPT_CHARS, WebValueRules.read("y".repeat(10_000), "")!!.length)
    }

    @Test fun itIsAskedAsOftenAsTheUserSetsAndNotMoreThanOnceAMinute() {
        assertEquals(5, WebValueRules.every.default)
        assertEquals(1..1440, WebValueRules.every.range)
        assertEquals(60_000L, WebValueRules.everyMs(0))
        assertEquals(5 * 60_000L, WebValueRules.everyMs(5))
        assertEquals(1440 * 60_000L, WebValueRules.everyMs(5000))
        val good = Reading("1", "", 1)
        assertEquals(15 * 60_000L, WebValueRules.every(good, 15))
        assertEquals("no value is no failure", 15 * 60_000L, WebValueRules.every(Reading(null, "", 1), 15))
        assertEquals("no answer: its own pace", 15 * 60_000L, WebValueRules.every(good.copy(failure = Failure.NO_ANSWER, misses = 3), 15))
        assertEquals("told to slow down: half an hour at least", 30 * 60_000L, WebValueRules.every(good.copy(failure = Failure.SLOW_DOWN), 5))
        assertEquals("or as long as it said", 90 * 60_000L, WebValueRules.every(good.copy(failure = Failure.SLOW_DOWN, retryAfterSec = 5400), 5))
        assertEquals("or its own, longer pace", 1440 * 60_000L, WebValueRules.every(good.copy(failure = Failure.SLOW_DOWN), 1440))
        for (misses in 0..10) {
            val wait = WebValueRules.every(good.copy(failure = Failure.OFFLINE, misses = misses), 15)
            assertTrue("$misses: $wait", wait in 60_000L..15 * 60_000L)
        }
    }

    @Test fun aReadingPrintsNoValue() {
        val r = Reading("secret-21.5", "a.b", 1)
        assertTrue(r.toString(), "secret" !in r.toString() && "a.b" !in r.toString())
    }

    @Test fun theStagesAreTheirOwnAndOnlyThose() {
        val now = 1_776_000_000_000L
        assertEquals("21.46", WebValueRules.sample("value", 0)!!.reading!!.value)
        assertNull(WebValueRules.sample("novalue", now)!!.reading!!.value)
        assertEquals(Failure.OFFLINE, WebValueRules.sample("offline", now)!!.reading!!.failure)
        assertNull(WebValueRules.sample("nourl", now)!!.host)
        assertNull(WebValueRules.sample("no-such-state", now))
    }
}

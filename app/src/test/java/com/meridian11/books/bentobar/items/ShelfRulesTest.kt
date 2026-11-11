package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.SourceScan
import com.meridian11.books.bentobar.items.ShelfRules.Entry
import com.meridian11.books.bentobar.items.ShelfRules.Kind
import com.meridian11.books.bentobar.items.ShelfRules.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the shelf takes, in what order, and how it keeps it. */
class ShelfRulesTest {
    private fun texts(entries: List<Entry>) = entries.map { it.text }

    @Test fun theNewestIsFirstAndTwentyAreKept() {
        var held = emptyList<Entry>()
        for (i in 1..25) held = ShelfRules.take(held, listOf("note $i"))
        assertEquals(ShelfRules.MOST, held.size)
        assertEquals("note 25", held.first().text)
        assertEquals("note 6", held.last().text)
        // Several at once (one drop of a clip with several items): the clip's first comes first.
        assertEquals(listOf("a", "b", "x"), texts(ShelfRules.take(listOf(Entry("x", Kind.TEXT)), listOf("a", "b"))))
    }

    @Test fun whatIsHeldIsNotHeldTwice() {
        val held = ShelfRules.take(emptyList(), listOf("one", "two"))
        assertEquals(held, ShelfRules.take(held, listOf("two")))
        assertEquals(held, ShelfRules.take(held, listOf("  two\n")))
        assertEquals(listOf("three", "one", "two"), texts(ShelfRules.take(held, listOf("three", "three"))))
    }

    @Test fun aTextOverTheLimitIsRefused() {
        val long = "x".repeat(ShelfRules.LONGEST + 1)
        assertEquals(emptyList<Entry>(), ShelfRules.take(emptyList(), listOf(long)))
        assertEquals(1, ShelfRules.take(emptyList(), listOf("x".repeat(ShelfRules.LONGEST))).size)
    }

    @Test fun aLinkIsALinkOnlyAlone() {
        assertEquals(Kind.LINK, ShelfRules.kind("https://a.example/x"))
        assertEquals(Kind.LINK, ShelfRules.kind("http://a.example"))
        assertEquals(Kind.LINK, ShelfRules.kind("HTTPS://A.example/x?y=1#z"))
        assertEquals(Kind.TEXT, ShelfRules.kind("see https://a.example"))
        assertEquals(Kind.TEXT, ShelfRules.kind("https://a.example/x\nhttps://b.example"))
        assertEquals(Kind.TEXT, ShelfRules.kind("ftp://a.example/x"))
        assertEquals(Kind.TEXT, ShelfRules.kind("https://"))
        assertEquals(Kind.LINK, ShelfRules.take(emptyList(), listOf(" https://a.example/x ")).single().kind)
    }

    @Test fun blanksAreNothing() {
        assertEquals(emptyList<Entry>(), ShelfRules.take(emptyList(), listOf("", "   ", "\n\t")))
    }

    @Test fun aLineIsTheFirstLineCutWithAnEllipsis() {
        assertEquals("first", ShelfRules.line(Entry("first\nsecond", Kind.TEXT)))
        assertEquals("ab…", ShelfRules.line(Entry("abcdef", Kind.TEXT), limit = 3))
        assertEquals("abc", ShelfRules.line(Entry("abc", Kind.TEXT), limit = 3))
        // Never half a character: an emoji is one, though Java counts it as two.
        assertEquals("a😀…", ShelfRules.line(Entry("a😀😀😀", Kind.TEXT), limit = 3))
        // A text that starts with empty lines says its first words.
        assertEquals("words", ShelfRules.line(Entry("\n\n  words", Kind.TEXT)))
    }

    @Test fun aDropGivesItsTextsAndRefusesFiles() {
        val parts = listOf(
            Part(text = "plain words", uri = null),
            Part(text = null, uri = "https://a.example/page"),
            Part(text = "a file's name", uri = "content://media/external/1"),
            Part(text = null, uri = "file:///sdcard/notes.txt"),
            Part(text = null, uri = "CONTENT://media/2"),
            Part(text = null, uri = null),
        )
        assertEquals(listOf("plain words", "https://a.example/page"), ShelfRules.dropped(parts))
    }

    @Test fun whatIsKeptReadsBackAsItWas() {
        val entries = ShelfRules.take(emptyList(), listOf("https://a.example/x", "two\nlines: 12:3", "😀 ünïcode", "4:", ":"))
        val kept = ShelfRules.encode(entries)
        assertEquals(entries, ShelfRules.decode(kept))
        assertEquals(emptyList<Entry>(), ShelfRules.decode(ShelfRules.encode(emptyList())))
        // Twenty of the longest fit what a kept file may hold.
        val full = (1..ShelfRules.MOST).map { Entry("$it" + "x".repeat(ShelfRules.LONGEST - 2), Kind.TEXT) }
        assertTrue(ShelfRules.encode(full).length < 200_000)
        assertEquals(full, ShelfRules.decode(ShelfRules.encode(full)))
    }

    @Test fun aBrokenFileIsReadAsFarAsItGoes() {
        val kept = ShelfRules.encode(ShelfRules.take(emptyList(), listOf("one", "two")))
        assertEquals(listOf("one"), texts(ShelfRules.decode(kept.dropLast(2))))
        assertEquals(emptyList<Entry>(), ShelfRules.decode("not a shelf"))
        assertEquals(emptyList<Entry>(), ShelfRules.decode("99999999999999999999:x"))
        // What a file says is taken as the rules take it: nothing blank, nothing too long, nothing twice, twenty at most.
        val written = (1..30).joinToString("") { "1:${'a' + (it % 26)}" } + "0:" + "5:     "
        val read = ShelfRules.decode(written)
        assertEquals(read.map { it.text }.distinct(), read.map { it.text })
        assertTrue(read.size <= ShelfRules.MOST && read.none { it.text.isBlank() })
    }

    @Test fun theBarHandsADropToItsTypeOnlyBehindTheGuardAndOnlyText() {
        // A type's own code runs behind one guard (ItemGuard): a drop is input from another app, as a click is from the user.
        val bar = SourceScan.code(java.io.File("src/main/java/com/meridian11/books/bentobar/bar/BarUi.kt"))
        val calls = bar.lines().filter { ".onDrop(" in it }
        assertEquals(1, calls.size)
        for (line in calls) assertTrue(line, "Ticker.guarded(" in line)
        assertTrue("only a drag that carries text is taken up", "\"text/*\"" in bar && "takesDrops" in bar)
    }

    @Test fun anEntryCanBeTakenOff() {
        val held = ShelfRules.take(emptyList(), listOf("one", "two", "three"))
        assertEquals(listOf("one", "three"), texts(ShelfRules.without(held, "two")))
        assertEquals(held, ShelfRules.without(held, "four"))
    }
}

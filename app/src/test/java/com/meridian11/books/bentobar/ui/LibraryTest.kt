package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Display
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.items.ItemGroup
import com.meridian11.books.bentobar.items.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library's rules: what a remembered setup's line says, which setups stand under which type, and what the filter shows. */
class LibraryTest {
    private fun fresh(type: String, id: String = type) = Items.of(type)!!.fresh(id, Position.RIGHT)
    private fun words(item: ItemConfig, fresh: ItemConfig = fresh(item.type, item.id)) = setupWords(item, Items.of(item.type)!!, fresh)
    private fun own(text: String) = listOf<SetupWord>(SetupWord.Own(text))

    @Test fun aSetupIsNamedByItsOwnWordsItsCityFirst() {
        assertEquals(own("Tokyo"), words(fresh("clock").with("zone", "Asia/Tokyo")))
        assertEquals(own("San Juan"), words(fresh("clock").with("zone", "America/Argentina/San_Juan")))
        val lisbon = fresh("weather").with("city", "Lisbon").with("lat", "38.7").with("lon", "-9.1")
        assertEquals(own("Lisbon"), words(lisbon))
        assertEquals("the city before a label", own("Lisbon"), words(lisbon.with("label", "Home")))
        assertEquals("a clock's city before its label", own("London"), words(fresh("clock").with("zone", "Europe/London").with("label", "Mum")))
        // My location is the place, whatever city the item had before.
        assertEquals(listOf(SetupWord.Here), words(lisbon.with("where", "here")))
        assertEquals(listOf(SetupWord.Here), words(fresh("air").with("where", "here")))
    }

    @Test fun orByItsTextOrItsLabel() {
        assertEquals(own("Tea"), words(fresh("timer").with("label", "Tea")))
        assertEquals(own("Launch"), words(fresh("countdown").with("label", " Launch ")))
        // A Text item's words are its own once they are not the ones it came with.
        val text = ItemConfig("t", "text", options = mapOf("text" to "Hello"))
        assertEquals(own("Stand-up at 10"), words(text.with("text", "Stand-up at 10"), text))
        assertEquals(listOf(SetupWord.Changed), words(text.with("icon", "star"), text))
        // Nothing typed is no word.
        assertEquals(listOf(SetupWord.TextOnly), words(fresh("timer").with("label", "  ").copy(display = Display.TEXT)))
    }

    @Test fun withNoWordsOfItsOwnItIsNamedByWhatDiffers() {
        assertEquals(listOf(SetupWord.TextOnly), words(fresh("battery").copy(display = Display.TEXT)))
        assertEquals(listOf(SetupWord.IconOnly), words(fresh("battery").copy(display = Display.ICON)))
        // A quiet type put in the bar for good; any other type made to wait on its rule.
        assertEquals(listOf(SetupWord.Always), words(fresh("heat").copy(whenActive = false)))
        assertEquals(listOf(SetupWord.Rule), words(fresh("cpu").copy(whenActive = true)))
        // The rule's own number, while the item waits on it.
        val line = Items.of("devices")!!.trigger!!.threshold!!.key
        assertEquals(listOf(SetupWord.Rule), words(fresh("devices").with(line, "30")))
        assertEquals(listOf(SetupWord.Always), words(fresh("devices").with(line, "30").copy(whenActive = false)))
        assertEquals(listOf(SetupWord.IconOnly, SetupWord.Rule), words(fresh("cpu").copy(display = Display.ICON, whenActive = true)))
        // Only an option that has no word: the line still says something.
        assertEquals(listOf(SetupWord.Changed), words(fresh("clock").with("seconds", "false")))
        assertEquals(listOf(SetupWord.Changed), words(fresh("shortcut").copy(options = emptyMap())))
        // Its own words say it all: what else differs is not listed after them.
        assertEquals(own("Tokyo"), words(fresh("clock").with("zone", "Asia/Tokyo").copy(display = Display.TEXT)))
    }

    private fun shelf(type: String, name: String, blurb: String, vararg lines: String) =
        Shelf(Items.of(type)!!, name, blurb, inBar = false, lines = lines.mapIndexed { i, w -> Line(fresh(type, "$type$i").copy(section = Section.OFF), w) })

    private val shelves = listOf(
        shelf("clock", "Clock", "The time in another city", "London", "Tokyo"),
        shelf("battery", "Battery details", "Power draw in watts, temperature, time to full", "text only"),
        shelf("heat", "Heat", "How warm the device runs"),
        shelf("weather", "Weather", "Temperature and rain for a city", "Lisbon"),
    )
    private fun found(query: String) = shelves.matching(query).map { s -> s.name to s.lines.map { it.words } }

    @Test fun theFilterMatchesANameADescriptionAndALinesWords() {
        assertEquals(listOf("Clock" to listOf("London", "Tokyo")), found("clo"))
        assertEquals(listOf("Battery details" to listOf("text only")), found("WATTS"))
        assertEquals("a type whose line matches is shown with that line", listOf("Weather" to listOf("Lisbon")), found("lis"))
        assertEquals(listOf("Clock" to listOf("Tokyo")), found(" tok "))
        assertEquals(listOf("Clock", "Weather"), found("city").map { it.first })
        assertEquals(listOf("Battery details" to listOf("text only")), found("text only"))
        assertTrue(found("zzz").isEmpty())
    }

    @Test fun theCountIsOfRowsTypesAndLinesAlike() {
        assertEquals(8, shelves.rows())
        assertEquals("Clock and its two lines", 3, shelves.matching("clo").rows())
        assertEquals("Clock and the one line that matches", 2, shelves.matching("tok").rows())
        assertEquals(0, shelves.matching("zzz").rows())
    }

    @Test fun aForgottenLineKeepsItsPlaceAndIsMarked() {
        val london = fresh("clock", "c1").with("zone", "Europe/London").copy(section = Section.OFF)
        val tokyo = fresh("clock", "c2").with("zone", "Asia/Tokyo").copy(section = Section.OFF)
        val lines = shelvesOf(BarConfig(items = listOf(london, tokyo)), { "" }, { "" }, forgotten = "c1") { item, _ -> item.id }
            .first { it.type.type == "clock" }.lines
        assertEquals(listOf("c1" to true, "c2" to false), lines.map { it.item.id to it.forgotten })
    }

    @Test fun onlyAnOffItemThatCarriesASetupIsALine() {
        // A layout from 1.8, before its first change here: any item could be turned off there, also one as it came.
        val tea = fresh("timer", "t1").with("label", "Tea").copy(section = Section.OFF)
        val cfg = BarConfig(items = listOf(fresh("cpu"), fresh("timer", "t0").copy(section = Section.OFF), tea, fresh("storage").copy(section = Section.OFF)))
        val by = shelvesOf(cfg, { "" }, { "" }) { item, _ -> item.id }.associateBy { it.type.type }
        assertEquals(listOf(tea), by.getValue("timer").lines.map { it.item })
        assertTrue(by.getValue("storage").lines.isEmpty())
        assertTrue("and it is not in the bar either", !by.getValue("storage").inBar)
    }

    @Test fun noFilterShowsEverything() {
        assertSame(shelves, shelves.matching(""))
        assertSame(shelves, shelves.matching("   "))
    }

    @Test fun setupsStandUnderTheirTypeInTheListsOrderAndUnknownOnesNowhere() {
        val london = fresh("clock", "c1").with("zone", "Europe/London").copy(section = Section.OFF)
        val tokyo = fresh("clock", "c2").with("zone", "Asia/Tokyo").copy(section = Section.OFF)
        val cfg = BarConfig(items = listOf(fresh("cpu"), london, fresh("clock", "c3"), tokyo,
            ItemConfig("u", "from-a-newer-version", Section.OFF), fresh("battery").copy(section = Section.OFF, display = Display.TEXT)))
        val all = shelvesOf(cfg, { it.type.uppercase() }, { "about " + it.type }) { item, _ -> item.id }
        assertEquals("every type has its row, group by group", ItemGroup.entries.flatMap { Items.inGroup(it) }, all.map { it.type })
        val by = all.associateBy { it.type.type }
        assertEquals(listOf("c1", "c2"), by.getValue("clock").lines.map { it.words })
        assertEquals(listOf(london, tokyo), by.getValue("clock").lines.map { it.item })
        assertEquals(listOf("battery"), by.getValue("battery").lines.map { it.words })
        assertEquals("CLOCK", by.getValue("clock").name)
        assertEquals("about clock", by.getValue("clock").blurb)
        // In your bar: an item of the type outside Off.
        assertTrue(by.getValue("clock").inBar)
        assertTrue(by.getValue("cpu").inBar)
        assertTrue(!by.getValue("battery").inBar)
        assertTrue(all.none { s -> s.lines.any { it.item.id == "u" } })
    }
}

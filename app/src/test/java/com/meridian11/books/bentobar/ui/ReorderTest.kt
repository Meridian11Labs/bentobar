package com.meridian11.books.bentobar.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Part
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.moved
import com.meridian11.books.bentobar.data.partOf
import com.meridian11.books.bentobar.data.parts
import com.meridian11.books.bentobar.items.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drop-target math and the move it previews (the same one the Store saves). */
class ReorderTest {
    private val upper = Zone(Position.RIGHT, Part.UPPER)
    private val lower = Zone(Position.RIGHT, Part.LOWER)
    private val left = Zone(Position.LEFT, Part.UPPER)
    // An area's upper part from its heading, the lower from the line between the parts; a gap before the next area's heading.
    private val zones = listOf(left to 0f..200f, upper to 214f..400f, lower to 400f..500f)
    private val rows = mapOf(left to listOf(50f, 100f, 150f), upper to listOf(260f), lower to emptyList())
    private val here = DropSpot(left, 1)

    @Test fun indexCountsTheRowsAbove() {
        assertEquals(DropSpot(left, 0), dropSpot(40f, zones, rows, here))
        assertEquals(DropSpot(left, 2), dropSpot(120f, zones, rows, here))
        assertEquals(DropSpot(left, 3), dropSpot(190f, zones, rows, here))
    }

    @Test fun otherZonesAndEmptyOnes() {
        assertEquals(DropSpot(upper, 1), dropSpot(300f, zones, rows, here))
        assertEquals(DropSpot(lower, 0), dropSpot(450f, zones, rows, here))
    }

    @Test fun gapKeepsTheCurrentSpotAndEndsClamp() {
        assertEquals(here, dropSpot(207f, zones, rows, here))
        assertEquals(DropSpot(left, 0), dropSpot(-30f, zones, rows, here))
        assertEquals(DropSpot(lower, 0), dropSpot(900f, zones, rows, here))
        assertEquals("a list not yet measured keeps the spot", here, dropSpot(100f, emptyList(), rows, here))
    }

    @Test fun movedMatchesSectionIndexes() {
        val items = listOf(ItemConfig("a", "x"), ItemConfig("b", "x"), ItemConfig("c", "x", Section.HIDDEN), ItemConfig("d", "x", Section.HIDDEN))
        assertEquals(listOf("b", "a", "c", "d"), items.moved("a", Section.SHOWN, 1).map { it.id })
        assertEquals(listOf("b", "c", "a", "d"), items.moved("a", Section.HIDDEN, 1).map { it.id })
        assertEquals(Section.HIDDEN, items.moved("a", Section.HIDDEN, 1).first { it.id == "a" }.section)
        assertEquals(listOf("a", "b", "c", "d"), items.moved("nope", Section.OFF, 0).map { it.id })
        assertEquals(listOf("b", "c", "d", "a"), items.moved("a", Section.OFF, 5).map { it.id })
    }

    // The whole page: the preview across the top, the list's card with two lanes side by side, the panel's card beside it.
    private val leftLower = Zone(Position.LEFT, Part.LOWER)
    private val page = DropMap(
        list = Rect(0f, 100f, 620f, 600f),
        zones = listOf(left to Rect(0f, 100f, 300f, 340f), leftLower to Rect(0f, 340f, 300f, 600f),
            upper to Rect(320f, 100f, 620f, 340f), lower to Rect(320f, 340f, 620f, 600f)),
        rows = mapOf(left to listOf(150f, 200f), leftLower to emptyList(), upper to listOf(150f, 200f, 250f), lower to listOf(400f)),
        preview = Rect(0f, 0f, 1000f, 52f),
        areas = mapOf(Position.LEFT to 100f..200f, Position.CENTER to 400f..500f, Position.RIGHT to 600f..900f),
        chips = mapOf(upper to listOf(650f, 750f, 850f)),
        shelf = Rect(640f, 100f, 1000f, 600f),
    )
    private val rule = Items.all.first { it.canBeActive && it.type != Store.PINNED_LEFT && !it.addsWhenActive }.type
    private val plain = Items.all.first { !it.canBeActive && it.type != Store.PINNED_LEFT }.type
    private fun row(id: String, type: String = rule, area: Position = Position.RIGHT, section: Section = Section.SHOWN, active: Boolean = false) =
        ItemConfig(id, type, section, whenActive = active, area = area)
    private val bar = BarConfig(items = listOf(row("a"), row("b"), row("c"), row("w", active = true, section = Section.HIDDEN)))
    private fun BarConfig.at(id: String, x: Float, y: Float, from: From = From.BAR, type: String = rule, map: DropMap = page, current: Drop = Drop.None) =
        dropAt(Offset(x, y), y, map, lift(Carried(id, from, type)), current)
    private fun BarConfig.order(area: Position = Position.RIGHT) = parts(area).let { p -> p.upper.map { it.id } to p.lower.map { it.id } }

    @Test fun aPointInTheListIsAPlaceInTheLaneAndThePartUnderIt() {
        assertEquals(Drop.At(DropSpot(upper, 2)), bar.at("a", 400f, 220f))
        assertEquals(Drop.At(DropSpot(lower, 1)), bar.at("a", 400f, 480f))
        // The lane is told by where the pointer is across the card, the place by the height of the row's middle.
        assertEquals(Drop.At(DropSpot(left, 1), starts = true), bar.at("a", 100f, 160f))
        assertEquals("between two lanes the nearer one", Drop.At(DropSpot(left, 2), starts = true), bar.at("a", 305f, 300f))
        assertEquals("with the lanes in one column a lane is told by height alone", DropSpot(lower, 0),
            listSpot(40f, 450f, zones.map { (z, r) -> z to Rect(0f, r.start, 600f, r.endInclusive) }, rows, null))
        assertNull("a list not yet measured has no place", listSpot(40f, 450f, emptyList(), rows, null))
    }

    @Test fun thePreviewTakesADropByAreaAndTheChipsBesideIt() {
        assertEquals(Drop.At(DropSpot(upper, 0)), bar.at("c", 610f, 20f))
        assertEquals(Drop.At(DropSpot(upper, 2)), bar.at("c", 800f, 20f))
        assertEquals("between two areas the nearer one", Drop.At(DropSpot(Zone(Position.CENTER, Part.UPPER), 0), starts = true), bar.at("c", 540f, 20f))
        // A row keeps its part on the way to another area; a chip that is not drawn right now counts by its place in the list.
        val chips = page.copy(chips = mapOf(lower to listOf(null, 700f, null)))
        assertEquals(Drop.At(DropSpot(lower, 2)), bar.at("w", 720f, 20f, map = chips))
        assertEquals(Drop.At(DropSpot(leftLower, 0), starts = true), bar.at("w", 150f, 20f))
        assertEquals("a preview with no areas measured takes nothing", Drop.None, bar.at("c", 610f, 20f, map = page.copy(areas = emptyMap())))
    }

    @Test fun theLibraryTakesARowFromTheBarAndNothingThatCameFromIt() {
        assertEquals(Drop.Off, bar.at("a", 700f, 300f))
        assertEquals(Drop.None, bar.at("new", 700f, 300f, From.TYPE))
        assertEquals(Drop.None, BarConfig(items = bar.items + row("o", section = Section.OFF)).at("o", 700f, 300f, From.LINE))
        assertEquals("outside the list, the preview and the library", Drop.None, bar.at("a", 630f, 300f))
        assertEquals(Drop.None, bar.at("a", 400f, 80f))
        assertEquals("the layout without it", listOf("b", "c") to listOf("w"), bar.dropped(Carried("a", From.BAR, rule), Drop.Off).order())
        assertSame("a type let go over the library adds nothing", bar, bar.dropped(Carried("new", From.TYPE, rule), Drop.Off))
    }

    @Test fun aPointAboveNextMeetingLandsAfterIt() {
        val cfg = BarConfig(items = listOf(row("e", Store.PINNED_LEFT), row("a"), row("b")))
        val drop = cfg.at("b", 400f, 110f)
        assertEquals(Drop.At(DropSpot(upper, 1)), drop)
        assertEquals(listOf("e", "b", "a"), cfg.dropped(Carried("b", From.BAR, rule), drop).order().first)
        assertEquals(DropLine(Says.MOVE_AFTER, "e"), cfg.dropLine(Carried("b", From.BAR, rule), drop, several = false))
        assertEquals("in the preview too", Drop.At(DropSpot(upper, 1)), cfg.at("b", 610f, 20f))
        assertEquals("Next meeting itself stays first", listOf("e", "a", "b"),
            cfg.dropped(Carried("e", From.BAR, Store.PINNED_LEFT), cfg.at("e", 400f, 240f)).order().first)
    }

    @Test fun aTypeWithNoRuleOverTheLowerPartHasNoTargetAndTheReason() {
        val cfg = BarConfig(items = listOf(row("a"), row("p", plain)))
        assertEquals(HiddenMode.SHOW_ALL, cfg.hiddenMode)
        val drop = cfg.at("p", 400f, 480f, type = plain)
        assertEquals(Drop.Refused(lower), drop)
        assertSame("a drop there changes nothing", cfg, cfg.dropped(Carried("p", From.BAR, plain), drop))
        assertEquals(DropLine(Says.NO_RULE), cfg.dropLine(Carried("p", From.BAR, plain), drop, several = false))
        assertEquals(Drop.Refused(lower), cfg.at("new", 400f, 480f, From.TYPE, plain))
        assertEquals(DropLine(Says.NO_RULE_ADD), cfg.dropLine(Carried("new", From.TYPE, plain), drop, several = false))
        // Behind ‹ anything can wait.
        val behind = cfg.copy(hiddenMode = HiddenMode.CLICK)
        assertEquals(Drop.At(DropSpot(lower, 1)), behind.at("p", 400f, 480f, type = plain))
        assertEquals("and a type from the library too", Drop.At(DropSpot(lower, 1)), behind.at("new", 400f, 480f, From.TYPE, plain))
        assertSame("a place it can't take adds nothing", cfg, cfg.dropped(Carried("new", From.TYPE, plain), Drop.At(DropSpot(lower, 0))))
    }

    @Test fun aDropOnAnAreaWithNoItemsStartsIt() {
        val c = Carried("b", From.BAR, rule)
        val drop = bar.at("b", 150f, 20f)
        assertEquals(Drop.At(DropSpot(left, 0), starts = true), drop)
        val after = bar.dropped(c, drop)
        assertEquals(listOf(Position.LEFT, Position.RIGHT), lanesOnScreen(after, emptySet()))
        assertEquals(listOf("b") to emptyList<String>(), after.order(Position.LEFT))
        assertEquals(DropLine(Says.STARTS, area = Position.LEFT), bar.dropLine(c, drop, several = false))
        // The only row of an area, let go on its own area: nothing starts.
        val alone = BarConfig(items = listOf(row("a"), row("l", area = Position.LEFT)))
        assertEquals(Drop.At(DropSpot(left, 0)), alone.at("l", 150f, 20f))
        assertEquals(Drop.At(DropSpot(upper, 1)), alone.at("l", 800f, 20f, map = page.copy(chips = mapOf(upper to listOf(650f)))))
        assertEquals("a type from the library starts one too", Drop.At(DropSpot(left, 0), starts = true), bar.at("new", 150f, 20f, From.TYPE))
    }

    @Test fun theLineUnderThePreviewSaysWhatADropDoes() {
        val a = Carried("a", From.BAR, rule)
        assertEquals(DropLine(Says.MOVE_AFTER, "c"), bar.dropLine(a, Drop.At(DropSpot(upper, 2)), several = false))
        assertEquals(DropLine(Says.MOVE_BEFORE, "b"), bar.dropLine(a, Drop.At(DropSpot(upper, 0)), several = false))
        assertEquals(DropLine(Says.MOVE_AFTER, "w"), bar.dropLine(a, Drop.At(DropSpot(lower, 1)), several = false))
        assertEquals("with lanes on screen it names the area", DropLine(Says.MOVE_AFTER, "c", Position.RIGHT), bar.dropLine(a, Drop.At(DropSpot(upper, 2)), several = true))
        val one = BarConfig(items = listOf(row("a")))
        assertEquals(DropLine(Says.MOVE_TO, area = Position.RIGHT, part = Part.LOWER), one.dropLine(a, Drop.At(DropSpot(lower, 0)), several = false))
        val new = Carried("new", From.TYPE, rule)
        assertEquals(DropLine(Says.ADD_AFTER, "a"), bar.dropLine(new, Drop.At(DropSpot(upper, 1)), several = false))
        assertEquals(DropLine(Says.ADD_BEFORE, "a"), bar.dropLine(new, Drop.At(DropSpot(upper, 0)), several = false))
        assertEquals(DropLine(Says.ADD_TO, area = Position.RIGHT, part = Part.UPPER), BarConfig().dropLine(new, Drop.At(DropSpot(upper, 0)), several = false))
        assertEquals(DropLine(Says.OFF), bar.dropLine(a, Drop.Off, several = false))
        assertEquals(DropLine(Says.NONE), bar.dropLine(a, Drop.None, several = false))
    }

    @Test fun theCarriedCopyIsTheItemAsItWouldBeWhereItHangs() {
        val waits = Items.all.first { it.addsWhenActive }
        val new = Carried("new", From.TYPE, waits.type)
        fun words(cfg: BarConfig, item: ItemConfig?) = item != null && cfg.partOf(item) == Part.LOWER && item.whenActive
        // A type that the + puts under the line, carried from the library: above the line it is always in the bar.
        val above = bar.carriedAs(new, Drop.At(DropSpot(upper, 1)))
        assertEquals(Section.SHOWN, above?.section)
        assertTrue("above the line it says nothing about when it shows", !words(bar, above))
        assertTrue("under the line it shows when its rule says", words(bar, bar.carriedAs(new, Drop.At(DropSpot(lower, 0)))))
        assertEquals("in the area it hangs over", Position.LEFT, bar.carriedAs(new, Drop.At(DropSpot(left, 0), starts = true))?.area)
        // Over no place it is in no part of the bar, and says nothing about when it shows, in any hidden mode.
        for (drop in listOf(Drop.None, Drop.Off, Drop.Refused(lower))) for (mode in HiddenMode.entries) {
            val cfg = bar.copy(hiddenMode = mode)
            val nowhere = cfg.carriedAs(new, drop)
            assertEquals(waits.type, nowhere?.type)
            assertTrue("$drop, $mode", !words(cfg, nowhere))
        }
        // A row of the bar: what it would be at the place, and as it is over none.
        val w = Carried("w", From.BAR, rule)
        assertTrue(words(bar, bar.carriedAs(w, Drop.None)))
        assertTrue(words(bar, bar.carriedAs(w, Drop.Off)))
        assertTrue("carried above the line it is always in the bar", !words(bar, bar.carriedAs(w, Drop.At(DropSpot(upper, 0)))))
        assertTrue("a row from above the line waits under it", words(bar, bar.carriedAs(Carried("a", From.BAR, rule), Drop.At(DropSpot(lower, 1)))))
        // A remembered line: as it comes back at the place, and as it was kept over none.
        val kept = BarConfig(items = bar.items + row("o", section = Section.OFF, active = true))
        val o = Carried("o", From.LINE, rule)
        assertEquals(Section.SHOWN, kept.carriedAs(o, Drop.At(DropSpot(upper, 0)))?.section)
        assertEquals(Section.OFF, kept.carriedAs(o, Drop.None)?.section)
        assertNull("something that is not in the layout", bar.carriedAs(Carried("gone", From.BAR, rule), Drop.None))
    }

    @Test fun aTypeAndARememberedLineLandWhereTheyAreDropped() {
        val added = bar.dropped(Carried("new", From.TYPE, rule), Drop.At(DropSpot(upper, 1)))
        assertEquals(listOf("a", "new", "b", "c") to listOf("w"), added.order())
        val below = bar.dropped(Carried("new", From.TYPE, rule), Drop.At(DropSpot(lower, 1)))
        assertEquals(listOf("a", "b", "c") to listOf("w", "new"), below.order())
        assertTrue("under the line it shows only when active", below.items.first { it.id == "new" }.whenActive)
        val kept = row("o", section = Section.OFF).copy(options = mapOf("k" to "v"))
        val back = BarConfig(items = bar.items + kept).dropped(Carried("o", From.LINE, rule), Drop.At(DropSpot(left, 0), starts = true))
        assertEquals(listOf("o") to emptyList<String>(), back.order(Position.LEFT))
        assertEquals("as it was", mapOf("k" to "v"), back.items.first { it.id == "o" }.options)
    }

    @Test fun aCancelledDragLeavesTheLayoutAsItWas() {
        val a = Carried("a", From.BAR, rule)
        assertSame(bar, bar.dropped(a, Drop.None))
        assertSame(bar, bar.dropped(Carried("new", From.TYPE, rule), Drop.None))
        // While it is carried the list has the row where it would land, nowhere over the library, and at home over nothing.
        assertSame(bar, bar.showing(a, Drop.None))
        assertEquals(listOf("b", "c") to listOf("w"), bar.showing(a, Drop.Off).order())
        assertTrue("and the item is still in the layout's list of what could come back", bar.showing(a, Drop.Off).items.none { it.id == "a" })
        assertEquals(listOf("b", "a", "c") to listOf("w"), bar.showing(a, Drop.At(DropSpot(upper, 1))).order())
        assertEquals("the same place is no change", bar, bar.dropped(a, Drop.At(DropSpot(upper, 0))))
        assertEquals(Part.UPPER, bar.partOf(bar.items.first()))
    }

    @Test fun thePreviewHasAZoneForEveryAreaAlsoOneNotInUse() {
        // Left from the clock's end, Right before the system icons, the Middle centred in what they leave.
        assertEquals(mapOf(Position.RIGHT to 600f..900f, Position.LEFT to 100f..196f, Position.CENTER to 452f..548f),
            previewZones(100f, 900f, mapOf(Position.RIGHT to 600f..900f), wide = 96f, gap = 8f, least = 32f))
        assertEquals(mapOf(Position.LEFT to 100f..196f, Position.RIGHT to 804f..900f, Position.CENTER to 452f..548f),
            previewZones(100f, 900f, emptyMap(), 96f, 8f, 32f))
        // A neighbour moves the Middle only as far as it must, and a zone with no room is not offered.
        assertEquals(mapOf(Position.RIGHT to 500f..900f, Position.LEFT to 100f..196f, Position.CENTER to 396f..492f),
            previewZones(100f, 900f, mapOf(Position.RIGHT to 500f..900f), 96f, 8f, 32f))
        assertEquals(mapOf(Position.RIGHT to 130f..900f), previewZones(100f, 900f, mapOf(Position.RIGHT to 130f..900f), 96f, 8f, 32f))
        assertEquals(Position.CENTER, areaAt(470f, mapOf(Position.LEFT to 100f..196f, Position.CENTER to 452f..548f)))
        assertEquals(Position.LEFT, areaAt(20f, mapOf(Position.LEFT to 100f..196f, Position.CENTER to 452f..548f)))
        assertNull(areaAt(20f, emptyMap()))
    }

    @Test fun theFreeSpaceIsBetweenTheClockAndTheIconsWhicheverSideEachIsOn() {
        assertEquals(116f to 888f, freeSpace(clock = 20f..100f, icons = 900f..980f, width = 1000f, beside = 16f, before = 12f))
        // Mirrored: the icons at the left, the clock at the right, and the same room kept free beside each.
        assertEquals(112f to 884f, freeSpace(clock = 900f..980f, icons = 20f..100f, width = 1000f, beside = 16f, before = 12f))
        // Not measured yet: the bar's ends.
        assertEquals(16f to 988f, freeSpace(null, null, 1000f, 16f, 12f))
        // And every area has its zone there, Left at the left, also mirrored.
        val (lo, hi) = freeSpace(clock = 900f..980f, icons = 20f..100f, width = 1000f, beside = 16f, before = 12f)
        assertEquals(mapOf(Position.LEFT to 112f..208f, Position.RIGHT to 788f..884f, Position.CENTER to 450f..546f),
            previewZones(lo, hi, emptyMap(), 96f, 8f, 32f))
    }

    @Test fun aZoneRunsFromItsHeadingDownItsLane() {
        // Two lanes side by side with one line between the parts across both, as the list registers them.
        val line = Rect(0f, 340f, 620f, 360f)
        val heads = mapOf(left to Rect(0f, 100f, 300f, 130f), upper to Rect(320f, 100f, 620f, 130f), leftLower to line, lower to line)
        assertEquals(listOf(left to Rect(0f, 100f, 300f, 340f), leftLower to Rect(0f, 340f, 300f, 600f),
            upper to Rect(320f, 100f, 620f, 340f), lower to Rect(320f, 340f, 620f, 600f)), zoneRects(heads, 600f))
        // In one column each heading is as wide as the list and the next one down ends the zone.
        val stacked = mapOf(left to Rect(0f, 0f, 600f, 30f), leftLower to Rect(0f, 200f, 600f, 220f), upper to Rect(0f, 300f, 600f, 330f))
        assertEquals(listOf(left to Rect(0f, 0f, 600f, 200f), leftLower to Rect(0f, 200f, 600f, 300f), upper to Rect(0f, 300f, 600f, 700f)), zoneRects(stacked, 700f))
    }

    @Test fun theInsertionBarStandsWhereTheItemWould() {
        val chips = mapOf("a" to 600f..640f, "c" to 700f..760f)
        val zone = 580f..900f
        assertEquals("before the next chip", 696f, insertionX(listOf("a", "x", "c"), "x", chips, zone, Position.RIGHT, 8f), 0f)
        assertEquals("a row the bar isn't drawing is passed over", 696f, insertionX(listOf("a", "x", "b", "c"), "x", chips, zone, Position.RIGHT, 8f), 0f)
        assertEquals("after the last", 764f, insertionX(listOf("a", "c", "x"), "x", chips, zone, Position.RIGHT, 8f), 0f)
        assertEquals(900f, insertionX(listOf("x"), "x", emptyMap(), zone, Position.RIGHT, 8f), 0f)
        assertEquals(580f, insertionX(listOf("x"), "x", emptyMap(), zone, Position.LEFT, 8f), 0f)
        assertEquals(740f, insertionX(listOf("x"), "x", emptyMap(), zone, Position.CENTER, 8f), 0f)
    }
}

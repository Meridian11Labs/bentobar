package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Part
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.moved
import org.junit.Assert.assertEquals
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
}

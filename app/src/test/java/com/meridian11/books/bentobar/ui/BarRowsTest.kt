package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Part
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.hide
import com.meridian11.books.bentobar.data.removed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The lines of the bar's one list, and the place a removed row keeps open. */
class BarRowsTest {
    private fun item(id: String, area: Position = Position.RIGHT, section: Section = Section.SHOWN, rule: Boolean = false) =
        ItemConfig(id, "cpu", section, whenActive = rule, area = area)
    private val upperRight = Zone(Position.RIGHT, Part.UPPER)
    private val lowerRight = Zone(Position.RIGHT, Part.LOWER)

    /** The list as words: a head, an area's name, the line, a row's id, a slip. */
    private fun lines(cfg: BarConfig, slip: Slip? = null, dragging: Boolean = false) = listEntries(cfg, slip, dragging).map {
        when (it) {
            is Entry.ListHead -> "head ${it.area}"
            is Entry.AreaHead -> "area ${it.area}" + if (it.first) " first" else ""
            is Entry.PartLine -> "line"
            is Entry.Row -> it.item.id + if (it.zone.part == Part.LOWER) " below" else ""
            is Entry.Removed -> "slip ${it.slip.item.id}"
            Entry.Empty -> "empty"
        }
    }

    @Test fun withOneAreaTheListIsOneHeadTheRowsAndAlwaysTheLine() {
        val cfg = BarConfig(items = listOf(item("a"), item("w", section = Section.HIDDEN, rule = true), item("b"), item("off", section = Section.OFF)))
        assertEquals(listOf("head RIGHT", "a", "b", "line", "w below"), lines(cfg))
        assertEquals("the line is there with nothing under it: it is where a row is dropped", listOf("head RIGHT", "a", "line"), lines(BarConfig(items = listOf(item("a")))))
    }

    @Test fun inTheDefaultModeAHiddenItemWithoutARuleIsListedAboveAndWithTheChevronBelow() {
        val cfg = BarConfig(items = listOf(item("a"), item("h", section = Section.HIDDEN)))
        assertEquals(listOf("head RIGHT", "a", "h", "line"), lines(cfg))
        assertEquals(listOf("head RIGHT", "a", "line", "h below"), lines(cfg.copy(hiddenMode = HiddenMode.CLICK)))
    }

    @Test fun withSeveralAreasEachHasItsNameLeftFirstAndALineOnlyWhereSomethingWaits() {
        val cfg = BarConfig(items = listOf(item("r"), item("l", Position.LEFT), item("w", Position.LEFT, Section.HIDDEN, rule = true), item("m", Position.CENTER)))
        assertEquals(listOf("area LEFT first", "l", "line", "w below", "area CENTER", "m", "area RIGHT", "r"), lines(cfg))
        // While a row is carried every area shows where its lower part is.
        assertEquals(listOf("area LEFT first", "l", "line", "w below", "area CENTER", "m", "line", "area RIGHT", "r", "line"), lines(cfg, dragging = true))
    }

    @Test fun anAreaWithOnlyOffItemsHasNoHeadAndAnEmptyBarSaysSo() {
        val cfg = BarConfig(items = listOf(item("r"), item("gone", Position.LEFT, Section.OFF)))
        assertEquals(listOf("head RIGHT", "r", "line"), lines(cfg))
        assertEquals(listOf("head LEFT", "empty"), lines(BarConfig(position = Position.LEFT)))
        assertEquals(listOf("head RIGHT", "empty"), lines(BarConfig(position = Position.RIGHT, items = listOf(item("gone", section = Section.OFF)))))
    }

    @Test fun aRowsKeyIsItsIdWhereverItStandsAndNoTwoLinesShareAKey() {
        val cfg = BarConfig(items = listOf(item("r"), item("l", Position.LEFT), item("w", Position.LEFT, Section.HIDDEN, rule = true)))
        val keys = listEntries(cfg, null, dragging = true).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(Entry.Row(cfg.items[0], upperRight).key, Entry.Row(cfg.items[0], Zone(Position.LEFT, Part.LOWER)).key)
    }

    // ---- The slip ----

    private val three = BarConfig(items = listOf(item("a"), item("b"), item("c")))
    private fun removal(before: BarConfig, id: String) = Undo.Kept(before, before.removed(id), removed = id)

    @Test fun aRemovedRowsPlaceStaysOpenWhereItStood() {
        val kept = removal(three, "b")
        assertEquals(Slip(three.items[1], upperRight, 1), slipOf(kept, kept.after))
        assertEquals(listOf("head RIGHT", "a", "slip b", "c", "line"), lines(kept.after, slipOf(kept, kept.after)))
        val last = removal(three, "c")
        assertEquals(listOf("head RIGHT", "a", "b", "slip c", "line"), lines(last.after, slipOf(last, last.after)))
    }

    @Test fun theSlipOfTheLowerPartStandsUnderTheLine() {
        val cfg = BarConfig(items = listOf(item("a"), item("w", section = Section.HIDDEN, rule = true)))
        val kept = removal(cfg, "w")
        assertEquals(lowerRight, slipOf(kept, kept.after)?.zone)
        assertEquals(listOf("head RIGHT", "a", "line", "slip w"), lines(kept.after, slipOf(kept, kept.after)))
    }

    @Test fun theLastItemsSlipKeepsItsAreasHeadAndTheBarIsNotCalledEmpty() {
        val one = BarConfig(items = listOf(item("a", Position.LEFT)))
        val kept = removal(one, "a")
        assertEquals(listOf("head LEFT", "slip a", "line"), lines(kept.after, slipOf(kept, kept.after)))
        // The only item of one of two areas: its area keeps its name while the slip stands.
        val two = BarConfig(items = listOf(item("l", Position.LEFT), item("r")))
        val gone = removal(two, "l")
        assertEquals(listOf("area LEFT first", "slip l", "area RIGHT", "r"), lines(gone.after, slipOf(gone, gone.after)))
    }

    @Test fun theSlipClosesWithTheNextChangeToTheArrangementAndIsNoneForAChangeThatRemovedNothing() {
        val kept = removal(three, "b")
        assertNull(slipOf(kept, kept.after.hide("a")))
        assertNull(slipOf(Undo.Kept(three, three.hide("a")), three.hide("a")))
        assertNull(slipOf(null, three))
        // An option changed since is no change to the arrangement: Undo is still offered.
        val optioned = kept.after.copy(items = kept.after.items.map { if (it.id == "a") it.copy(options = mapOf("k" to "v")) else it })
        assertEquals("b", slipOf(kept, optioned)?.item?.id)
    }

    // ---- Order's words ----

    @Test fun ordinalsAreEnglishOnesWithTheTeensRight() {
        fun ends(vararg n: Int) = n.map { ordinalRes(it) }.toSet()
        assertEquals(setOf(R.string.order_st), ends(1, 21, 101))
        assertEquals(setOf(R.string.order_nd), ends(2, 22))
        assertEquals(setOf(R.string.order_rd), ends(3, 23))
        assertEquals(setOf(R.string.order_th), ends(4, 10, 11, 12, 13, 20, 111, 112, 113))
    }
}

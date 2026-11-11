package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BAR_ORDER
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.homeArea

/** The areas of [cfg] that hold a row, in the bar's order: none with nothing in the bar. */
internal fun rowAreas(cfg: BarConfig): List<Position> = BAR_ORDER.filter { a -> cfg.items.any { it.area == a && it.section != Section.OFF } }

/**
 * The lanes the Bar page has on screen, in the bar's order: the areas with rows and the ones "Add an area" [opened] that
 * are still empty; with nothing at all, the one a new item goes to. Everything about areas is on the page only while
 * this is more than one: the lanes and their heads, the brackets under the preview, Alt with ← or →, Area as the
 * three-way choice, the library's "Add to".
 */
internal fun lanesOnScreen(cfg: BarConfig, opened: Set<Position>): List<Position> {
    val rows = rowAreas(cfg)
    val home = if (rows.isEmpty()) cfg.homeArea() else null
    return BAR_ORDER.filter { it in rows || it in opened || it == home }
}

/** The lane a row of [area] goes to with Alt and an arrow: the next one on that side, none at the end. */
internal fun laneBeside(lanes: List<Position>, area: Position, right: Boolean): Position? {
    val at = lanes.indexOf(area)
    return if (at < 0) null else lanes.getOrNull(if (right) at + 1 else at - 1)
}

/**
 * Which arrow moves an item into the empty lane [area], as the lane says it: → when every other lane stands left of
 * it, ← otherwise, so between two it names the way from Right, where the bar's items are first.
 */
internal fun leadsRight(lanes: List<Position>, area: Position): Boolean {
    val at = BAR_ORDER.indexOf(area)
    return lanes.none { BAR_ORDER.indexOf(it) > at } && lanes.any { it != area }
}

/**
 * The area the last change closed, while that can still be taken back from [now]: it held rows before and holds none
 * after, and the bar still has some. The page says so in a slip with Undo above the list. [several]: more than one
 * lane was on screen before the change, which the page knows (an opened lane is its own). With one lane nothing
 * closes: the bar's last row keeps its place with its own slip, and an item that takes the one area with it to another
 * has only moved the list.
 */
internal fun foldOf(kept: Undo.Kept?, now: BarConfig, several: Boolean): Position? {
    if (!several || kept == null || !Undo.offered(kept, now)) return null
    val after = rowAreas(kept.after)
    if (after.isEmpty()) return null
    return rowAreas(kept.before).singleOrNull { it !in after }
}

/** The item the last change moved to another area, or null: the row the page marks "Moved" while a lane's closing is said. */
internal fun movedBy(kept: Undo.Kept?): String? {
    kept ?: return null
    val was = kept.before.items.associate { it.id to it.area }
    return kept.after.items.singleOrNull { it.section != Section.OFF && was[it.id] != null && was[it.id] != it.area }?.id
}

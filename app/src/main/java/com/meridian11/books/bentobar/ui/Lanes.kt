package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BAR_ORDER
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.ROOM_ORDER
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.areasInUse
import com.meridian11.books.bentobar.data.homeArea
import com.meridian11.books.bentobar.data.rowAreas
import com.meridian11.books.bentobar.data.stripPlan

/**
 * The lanes the Bar page has on screen, in the bar's order: the areas with rows and the ones "Add an area" [opened] that
 * are still empty; with nothing at all, the one a new item goes to. Everything about areas is on the page only while
 * this is more than one: the lanes and their heads, the brackets under the preview, Alt with ← or →, Area as the
 * three-way choice, the library's "Add to".
 */
internal fun lanesOnScreen(cfg: BarConfig, opened: Set<Position>): List<Position> {
    val rows = cfg.rowAreas()
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
 * has only moved the list. Nor does Look's command close one here ([Undo.Kept.allTo]): it emptied every other area on
 * purpose and says so itself, on Look.
 */
internal fun foldOf(kept: Undo.Kept?, now: BarConfig, several: Boolean): Position? {
    if (!several || kept == null || kept.allTo != null || !Undo.offered(kept, now)) return null
    val after = kept.after.rowAreas()
    if (after.isEmpty()) return null
    return kept.before.rowAreas().singleOrNull { it !in after }
}

/**
 * The item the last change moved to another area, or null: the row the page marks "Moved" while a lane's closing is said.
 * Look's command moved them all, even where only one stood elsewhere: no row is its own.
 */
internal fun movedBy(kept: Undo.Kept?): String? {
    if (kept == null || kept.allTo != null) return null
    val was = kept.before.items.associate { it.id to it.area }
    return kept.after.items.singleOrNull { it.section != Section.OFF && was[it.id] != null && was[it.id] != it.area }?.id
}

/**
 * The areas the bar has no room for right now: not the one that takes the free space first, and everything its strip
 * would draw is among the items that don't fit ([overflow], as the bar reports them; [active] as for [stripPlan]). Its
 * lane says so once, in a notice, and its rows say nothing about fitting. The areas are counted as the bar counts them
 * ([areasInUse]: an item of a type this version doesn't know takes no room), so the first is the bar's first.
 */
internal fun noRoomAreas(cfg: BarConfig, overflow: Set<String>, active: (ItemConfig) -> Boolean): Set<Position> {
    val used = cfg.areasInUse().let { inUse -> BAR_ORDER.filter { it in inUse } }
    if (used.size < 2 || overflow.isEmpty()) return emptySet()
    val first = ROOM_ORDER.first { it in used }
    return used.filter { area ->
        val drawn = cfg.stripPlan(area, active, expanded = false, overflow = false).visible
        area != first && drawn.isNotEmpty() && drawn.all { it.id in overflow }
    }.toSet()
}

/** The areas in use ([areasInUse], as the bar counts them) that take the free space before [area] does, first taker first: the ones its notice names. */
internal fun roomAhead(cfg: BarConfig, area: Position): List<Position> = cfg.areasInUse().let { used -> ROOM_ORDER.takeWhile { it != area }.filter { it in used } }

package com.meridian11.books.bentobar.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.BarOverflow
import com.meridian11.books.bentobar.data.BAR_ORDER
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Display
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Part
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Standing
import com.meridian11.books.bentobar.data.Step
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.canGoBelow
import com.meridian11.books.bentobar.data.carriesSetup
import com.meridian11.books.bentobar.data.partOf
import com.meridian11.books.bentobar.data.parts
import com.meridian11.books.bentobar.data.standing
import com.meridian11.books.bentobar.data.stepped
import com.meridian11.books.bentobar.data.undone
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.util.Sym
import com.meridian11.books.bentobar.util.SymIcon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.meridian11.books.bentobar.data.Pill
import com.meridian11.books.bentobar.bar.Strip
import com.meridian11.books.bentobar.bar.StripEntry
import com.meridian11.books.bentobar.bar.StripEvents
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.data.homeArea
import com.meridian11.books.bentobar.items.Ticker
import kotlin.math.roundToInt

/** A removed row's place, held open with Undo in it: the item as it was, the zone it stood in and its place there. */
data class Slip(val item: ItemConfig, val zone: Zone, val index: Int)

/**
 * The slip for the last change, if that took an item out of the bar and can still be taken back from [now] (the layout
 * is arranged as the removal left it). Any other change to the arrangement closes it.
 */
fun slipOf(kept: Undo.Kept?, now: BarConfig): Slip? {
    val id = kept?.removed ?: return null
    if (!Undo.offered(kept, now)) return null
    val item = kept.before.items.firstOrNull { it.id == id } ?: return null
    val part = kept.before.partOf(item) ?: return null
    val index = kept.before.parts(item.area)[part].indexOfFirst { it.id == id }
    return Slip(item, Zone(item.area, part), index.coerceIn(0, now.parts(item.area)[part].size))
}

/**
 * One line of the bar's list. A row's [key] is its item's id, so a row that changes part or area is the same row.
 * [lane] is the area the line belongs to, null for one that runs across them all.
 */
sealed interface Entry {
    val key: Any
    val lane: Position?

    /** The list's head, with one lane on screen: "In the bar · Left to right, as they appear". */
    data class ListHead(val area: Position) : Entry { override val key: Any get() = "head" to area; override val lane get() = area }
    /** An area's name and where it is, with several lanes on screen; [first] has no line above it in the one column. */
    data class AreaHead(val area: Position, val first: Boolean) : Entry { override val key: Any get() = "area" to area; override val lane get() = area }
    /** The line between an area's two parts, in the one column. */
    data class PartLine(val area: Position) : Entry { override val key: Any get() = "line" to area; override val lane get() = area }
    /** The one line between the two parts of every lane, side by side. */
    data object SharedLine : Entry { override val key: Any get() = "line" to 0; override val lane: Position? get() = null }
    data class Row(val item: ItemConfig, val zone: Zone) : Entry { override val key: Any get() = item.id; override val lane get() = zone.area }
    data class Removed(val slip: Slip) : Entry { override val key: Any get() = "slip" to slip.item.id; override val lane get() = slip.zone.area }
    /** Nothing is in the bar. */
    data object Empty : Entry { override val key: Any get() = "empty" to 0; override val lane: Position? get() = null }
    /** A lane with nothing in it: the one "Add an area" opened, with what to do with it. */
    data class EmptyLane(val area: Position) : Entry { override val key: Any get() = "lane" to area; override val lane get() = area }
    /** "Nothing here": a lane's lower part with no rows, side by side with the others'. */
    data class Blank(val area: Position) : Entry { override val key: Any get() = "blank" to area; override val lane get() = area }
    /** The area the last change closed, said above the list with Undo. */
    data class Fold(val area: Position) : Entry { override val key: Any get() = "fold" to area; override val lane: Position? get() = null }
    /** The bar has no room for this area right now. */
    data class NoRoom(val area: Position) : Entry { override val key: Any get() = "noroom" to area; override val lane get() = area }
    /** Room at the end of a part while something is dragged: half a row in the one column, a row in a lane, so an empty part can be dropped into. */
    data class Room(val zone: Zone) : Entry { override val key: Any get() = "room" to zone; override val lane get() = zone.area }
}

/**
 * The bar's list for the [lanes] on screen, Left first: for each its head, its upper part, the line and its lower
 * part. With one lane the line is always there (it says what the lower part is, and is where a row is dropped to put
 * it there). With several in one column ([abreast] false, the narrow window) a lane has its line only for a lower part
 * that has rows, or while a row is [dragging]; side by side there is one line for all, and a lower part without rows
 * says so. A [slip] stands where its row stood, and keeps its area on screen. A lane with nothing in it says what it
 * is for; [fold], the area that just closed, is said first; an area in [noRoom] says so under its head. While a row is
 * [dragging] every part ends in room to drop into, unless words already stand there (an empty lane's, "Nothing here").
 */
fun listEntries(cfg: BarConfig, slip: Slip?, dragging: Boolean, lanes: List<Position> = lanesOnScreen(cfg, emptySet()), abreast: Boolean = false,
                fold: Position? = null, noRoom: Set<Position> = emptySet()): List<Entry> {
    // The bar's last row, removed: its slip stands in its own area, whichever area an empty bar would add to.
    val alone = slip?.zone?.area?.takeIf { lanes.size < 2 && rowAreas(cfg).isEmpty() }
    val areas = if (alone != null) listOf(alone) else BAR_ORDER.filter { it in lanes || it == slip?.zone?.area }
    val several = areas.size > 1
    if (!several && slip == null && cfg.items.none { cfg.partOf(it) != null }) return listOf(Entry.ListHead(areas.first()), Entry.Empty)
    return buildList {
        if (fold != null) add(Entry.Fold(fold))
        areas.forEachIndexed { i, area ->
            add(if (several) Entry.AreaHead(area, first = i == 0) else Entry.ListHead(area))
            if (several && area in noRoom) add(Entry.NoRoom(area))
            val parts = cfg.parts(area)
            val bare = several && slip?.zone?.area != area && Part.entries.all { parts[it].isEmpty() }
            if (bare) add(Entry.EmptyLane(area))
            for (part in Part.entries) {
                val zone = Zone(area, part)
                val rows: List<Entry> = parts[part].map { Entry.Row(it, zone) }
                val here = slip?.takeIf { it.zone == zone }
                if (part == Part.LOWER) {
                    if (several && abreast) {
                        if (i == 0) add(Entry.SharedLine)
                        if (rows.isEmpty() && here == null) add(Entry.Blank(area))
                    } else if (!several || rows.isNotEmpty() || here != null || dragging) add(Entry.PartLine(area))
                }
                if (here == null) addAll(rows)
                else addAll(rows.toMutableList().apply { add(here.index.coerceIn(0, size), Entry.Removed(here)) })
                val worded = if (part == Part.UPPER) bare else several && abreast && rows.isEmpty() && here == null
                if (dragging && !worded) add(Entry.Room(zone))
            }
        }
    }
}

private val ROW_SHAPE = RoundedCornerShape(Radius.medium)
private val ROW_INSET = 12.dp

private val LANE_GAP = 8.dp
/** What a part grows by in the one column while something is dragged. */
private val ROOM = 24.dp

private enum class Band { TOP, HEAD, UPPER, LINE, LOWER }

/**
 * Where a line stands when the lanes are side by side: its lane (-1 across them all) and its band. [fill]: as tall as
 * the tallest upper part beside it (the empty lane's box).
 */
private data class Slot(val lane: Int, val band: Band, val fill: Boolean = false)

private fun slotOf(e: Entry, order: List<Position>): Slot {
    val lane = e.lane?.let(order::indexOf) ?: -1
    return when (e) {
        is Entry.Fold -> Slot(-1, Band.TOP)
        is Entry.ListHead, is Entry.AreaHead -> Slot(lane, Band.HEAD)
        is Entry.NoRoom, Entry.Empty -> Slot(lane, Band.UPPER)
        is Entry.EmptyLane -> Slot(lane, Band.UPPER, fill = true)
        Entry.SharedLine -> Slot(-1, Band.LINE)
        is Entry.PartLine, is Entry.Blank -> Slot(lane, Band.LOWER)
        is Entry.Row -> Slot(lane, if (e.zone.part == Part.UPPER) Band.UPPER else Band.LOWER)
        is Entry.Removed -> Slot(lane, if (e.slip.zone.part == Part.UPPER) Band.UPPER else Band.LOWER)
        is Entry.Room -> Slot(lane, if (e.zone.part == Part.UPPER) Band.UPPER else Band.LOWER)
    }
}

/**
 * The list's lines, laid out. In one column ([abreast] under 2) each stands under the one before, as wide as the card.
 * Side by side each lane has a column: what closed across the top, the heads in a row, the upper parts, the one line
 * across, the lower parts, so the line and the lower parts start at one height in every lane. Every line is a child
 * of this one layout in both forms: a row that changes lane keeps its node, its focus, and travels there.
 */
@Composable
private fun Lines(abreast: Int, content: @Composable () -> Unit) {
    Layout(content) { measurables, cs ->
        val width = cs.maxWidth
        if (abreast < 2) {
            val placed = measurables.map { it.measure(Constraints(maxWidth = width)) }
            return@Layout layout(width, placed.sumOf { it.height }) {
                var y = 0
                placed.forEach { it.place(0, y); y += it.height }
            }
        }
        val inset = ROW_INSET.roundToPx()
        val gap = LANE_GAP.roundToPx()
        val lane = ((width - 2 * inset - gap * (abreast - 1)) / abreast).coerceAtLeast(0)
        // A lane's lines keep their own insets, so each is measured that much wider than its lane and the lanes overlap by it.
        val inLane = Constraints(maxWidth = lane + 2 * inset)
        val slots = measurables.map { it.layoutId as Slot }
        val placed = arrayOfNulls<Placeable>(measurables.size)
        measurables.forEachIndexed { i, m -> if (!slots[i].fill) placed[i] = m.measure(if (slots[i].lane < 0) Constraints(maxWidth = width) else inLane) }
        fun tall(band: Band, at: Int) = slots.indices.sumOf { if (slots[it].band == band && slots[it].lane == at) placed[it]?.height ?: 0 else 0 }
        fun tallest(band: Band) = (-1 until abreast).maxOf { tall(band, it) }
        val beside = tallest(Band.UPPER)
        measurables.forEachIndexed { i, m ->
            if (slots[i].fill) placed[i] = m.measure(inLane.copy(minHeight = (beside - tall(Band.UPPER, slots[i].lane)).coerceAtLeast(0)))
        }
        val tops = IntArray(Band.entries.size + 1)
        Band.entries.forEach { tops[it.ordinal + 1] = tops[it.ordinal] + tallest(it) }
        layout(width, tops.last()) {
            val at = HashMap<Pair<Int, Band>, Int>()
            placed.forEachIndexed { i, p ->
                val slot = slots[i]
                val y = at[slot.lane to slot.band] ?: tops[slot.band.ordinal]
                p!!.place(if (slot.lane < 0) 0 else slot.lane * (lane + gap), y)
                at[slot.lane to slot.band] = y + p.height
            }
        }
    }
}

/**
 * The bar's card: the one list, or with several [lanes] on screen in a window with the room, the lanes side by side.
 * A click selects a row, a click on the card's empty part lets the selection go, and a row is dragged to a new place by
 * [reorder] (the gesture and the copy that floats are the page's): the list shows the opening where the row would land,
 * room at the end of each part, and the part under it tinted.
 */
@Composable
internal fun BarList(cfg: BarConfig, states: Map<String, ItemState>, selected: String?, edit: BarEdit, reorder: Reorder, lanes: Lanes,
                     modifier: Modifier = Modifier) {
    val c = Bento.colors
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val kept by Undo.last.collectAsState()
    val setup by Setup.state.collectAsState()
    val overflow by BarOverflow.ids.collectAsState()
    val narrow = LocalWindowLayout.current.kind == WindowClass.NARROW
    val several = lanes.several
    val abreast = several && !narrow
    // The Store has caught up with the drop: the row is where it landed.
    LaunchedEffect(cfg) { reorder.arrived() }
    AutoScroll(reorder, scroll)
    val shown = reorder.preview(cfg)
    val dragging = reorder.dragging
    val entries = remember(shown, kept, dragging != null, lanes, abreast) {
        // A lane that closed says so above the list; the removed row's own slip would stand in a lane that is gone.
        listEntries(shown, slipOf(kept, shown).takeIf { lanes.fold == null }, dragging != null, lanes.on, abreast, lanes.fold, lanes.noRoom)
    }
    val order = remember(entries) { entries.mapNotNull { it.lane }.distinct() }
    val added = Undo.added(kept, cfg)?.id
    val moved = if (lanes.fold != null) movedBy(kept) else null
    val grabbing = remember { PointerIcon(android.view.PointerIcon.TYPE_GRABBING) }
    Column(modifier.clip(RoundedCornerShape(Radius.large)).background(c.panel)
        .pointerInput(edit) { detectTapGestures { edit.letGo() } }
        .onGloballyPositioned { reorder.viewport = it }
        .verticalScroll(scroll).padding(vertical = Space.m).testTag("bar-list")) {
        val tint = c.accent
        Box(Modifier.fillMaxWidth().onGloballyPositioned { reorder.content = it }
            // The part a drop would land in, from under its heading to its end.
            .drawBehind {
                val zone = (reorder.drop as? Drop.At)?.spot?.zone?.takeIf { reorder.live } ?: return@drawBehind
                val at = reorder.rowsOf(zone)?.deflate(6.dp.toPx())?.takeIf { it.width > 0f && it.height > 0f } ?: return@drawBehind
                val round = CornerRadius(12.dp.toPx())
                drawRoundRect(tint.copy(alpha = 0.05f), at.topLeft, at.size, round)
                drawRoundRect(tint.copy(alpha = 0.45f), at.topLeft, at.size, round, style = Stroke(1.dp.toPx()))
            }
            .pointerHoverIcon(if (dragging != null) grabbing else PointerIcon.Default, overrideDescendants = dragging != null)) {
            Lines(if (abreast) order.size else 1) {
                // One call site for every line: a row that moves to another part or area keeps its node, and its focus.
                entries.forEach { e ->
                    key(e.key) {
                        val slot = Modifier.layoutId(slotOf(e, order))
                        when (e) {
                            is Entry.ListHead -> Head(stringResource(R.string.section_shown), stringResource(R.string.section_shown_help),
                                slot.onGloballyPositioned { reorder.headings[Zone(e.area, Part.UPPER)] = it }) { AddArea(lanes.on, edit) }
                            is Entry.AreaHead -> Head(stringResource(areaRes(e.area)), stringResource(whereRes(e.area)),
                                slot.onGloballyPositioned { reorder.headings[Zone(e.area, Part.UPPER)] = it }, rule = !abreast && !e.first, plus = !abreast) {
                                if (e.area == order.last() && lanes.on.size < BAR_ORDER.size) AddArea(lanes.on, edit)
                                if (!abreast) BentoIconButton(Sym.ADD, stringResource(R.string.areas_add_item, stringResource(areaRes(e.area))),
                                    { edit.addTo = e.area; edit.toLibrary() }, Modifier.testTag("add-to:${e.area}"), filled = false)
                            }
                            is Entry.PartLine -> PartLine(cfg.hiddenMode, slot.onGloballyPositioned { reorder.headings[Zone(e.area, Part.LOWER)] = it })
                            // Every lane's lower part starts at this one line.
                            Entry.SharedLine -> PartLine(cfg.hiddenMode, slot.onGloballyPositioned { at -> order.forEach { reorder.headings[Zone(it, Part.LOWER)] = at } })
                            is Entry.Row -> {
                                val over = e.item.id in overflow && e.zone.area !in lanes.noRoom
                                if (e.item.id == dragging) DropOpening(e.item.id, reorder, slot)
                                else ItemRow(e.item, shown, states[e.item.id], e.item.id == selected, over, mark = when (e.item.id) {
                                    added -> Mark.ADDED
                                    moved -> Mark.MOVED
                                    else -> null
                                }, setup.calendar, edit, reorder, lanes.on, stacked = narrow || abreast, slot)
                            }
                            is Entry.Removed -> SlipRow(e.slip, edit, slot)
                            Entry.Empty -> Text(stringResource(R.string.bar_empty), slot.padding(horizontal = Space.xl, vertical = Space.s),
                                style = Bento.type.small, color = c.ink2)
                            is Entry.EmptyLane -> EmptyLane(e.area, lanes.on, edit, slot)
                            is Entry.Blank -> Box(slot.fillMaxWidth().heightIn(min = Target.row).padding(horizontal = Space.xl).testTag("blank:${e.area}"),
                                contentAlignment = Alignment.CenterStart) { Text(stringResource(R.string.areas_nothing), style = Bento.type.help, color = c.ink3) }
                            is Entry.Fold -> FoldSlip(e.area, edit, slot)
                            is Entry.NoRoom -> NoRoom(e.area, cfg, slot)
                            is Entry.Room -> Spacer(slot.fillMaxWidth().height(if (abreast) Target.row else ROOM))
                        }
                    }
                }
            }
        }
    }
}

/** How long the drag keeps following after the last move: a little longer than the rows take to make room. */
private const val SETTLED_NS = 250_000_000L

/** While a row is dragged near the list's upper or lower edge, the list scrolls under it, and the opening follows. */
@Composable
private fun AutoScroll(reorder: Reorder, scroll: ScrollState) {
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val maxStep = with(density) { 14.dp.toPx() }
    LaunchedEffect(reorder.dragging) {
        if (reorder.dragging == null) return@LaunchedEffect
        // Frames run while the list scrolls and for as long as the rows take to make room, then rest until the pointer,
        // the target or the list (a wheel) moves again: a row held still costs nothing.
        snapshotFlow { reorder.stirred to scroll.value }.collectLatest {
            var rested = 0L
            var last = withFrameNanos { it }
            while (rested < SETTLED_NS) {
                val now = withFrameNanos { it }
                val v = reorder.edgeSpeed(edge, maxStep)
                rested = if (v != 0f && scroll.scrollBy(v) != 0f) 0L else rested + (now - last)
                last = now
                reorder.sync()
            }
        }
    }
}

/** Where an area is in the bar, in a few words after its name. */
internal fun whereRes(area: Position): Int = when (area) {
    Position.LEFT -> R.string.areas_left_where
    Position.CENTER -> R.string.areas_middle_where
    Position.RIGHT -> R.string.areas_right_where
}

/**
 * A head: the list's ("In the bar · Left to right, as they appear") or an area's ("Left · After the clock"), one
 * heading for a screen reader, and at its end what [trailing] puts there ("Add an area", an area's +). [rule]: a
 * hairline above it, between the areas of the one column; [plus]: the head ends in a button of a row's height.
 */
@Composable
private fun Head(name: String, where: String, modifier: Modifier, rule: Boolean = false, plus: Boolean = false, trailing: @Composable RowScope.() -> Unit) {
    val c = Bento.colors
    Column(modifier.fillMaxWidth()) {
        if (rule) Box(Modifier.fillMaxWidth().padding(Space.s).height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().heightIn(min = if (plus) Target.control else 36.dp).padding(start = Space.xl, end = if (plus) Space.s else 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = Bento.type.strong, color = c.ink, maxLines = 1)
                Text(" · $where", style = Bento.type.help, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

/**
 * "Add an area": the one thing about areas a bar with one has on screen. A click opens a short menu of the areas that
 * have no lane yet, each with where it is; a choice opens its lane, empty, and the keyboard goes there.
 */
@Composable
private fun AddArea(lanes: List<Position>, edit: BarEdit) {
    val c = Bento.colors
    val interaction = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.height(32.dp).states(interaction).background(if (open) c.sunk else Color.Transparent, ROW_SHAPE)
            .clickable(interaction, indication = null, role = Role.Button) { open = true }
            .padding(start = 10.dp, end = 6.dp).testTag("add-area"), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.areas_add), style = Bento.type.small.copy(fontWeight = FontWeight.Medium), color = c.ink2, maxLines = 1)
            Spacer(Modifier.width(Space.xs))
            SymIcon(Sym.EXPAND_MORE, size = 14.sp, color = c.ink2)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(268.dp), containerColor = c.raised) {
            BAR_ORDER.filter { it !in lanes }.forEach { area ->
                DropdownMenuItem(text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(areaRes(area)), style = Bento.type.label, color = c.ink, maxLines = 1)
                        Spacer(Modifier.width(Space.s))
                        Text(stringResource(whereRes(area)), style = Bento.type.help, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }, onClick = { open = false; edit.openArea(area) }, modifier = Modifier.testTag("add-area:$area"))
            }
        }
    }
}

/**
 * A lane with nothing in it, as "Add an area" opens it: what it is for, in the place its rows will take. It takes the
 * keyboard when it opens, and says there which key moves an item into it.
 */
@Composable
private fun EmptyLane(area: Position, lanes: List<Position>, edit: BarEdit, modifier: Modifier) {
    val c = Bento.colors
    val res = LocalResources.current
    val name = stringResource(areaRes(area))
    val spoken = stringResource(R.string.areas_empty_new_spoken, name, AreaKeys.spoken(res, leadsRight(lanes, area)))
    val interaction = remember { MutableInteractionSource() }
    val requester = remember { FocusRequester() }
    val focus = edit.focusLane
    LaunchedEffect(focus) {
        if (focus != area) return@LaunchedEffect
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        if (edit.focusLane == area) edit.focusLane = null
    }
    Box(modifier.fillMaxWidth().padding(horizontal = ROW_INSET).testTag("lane:$area").focusRequester(requester)
        .states(interaction, ground = false).focusable(interactionSource = interaction)
        .clearAndSetSemantics { contentDescription = spoken }) {
        Text(stringResource(R.string.areas_empty_new, name), Modifier.heightIn(min = 64.dp).padding(horizontal = Space.m, vertical = 6.dp),
            style = Bento.type.help, color = c.ink3)
    }
}

/** A lane closed with its last item: which one, across the top of the list, with the Undo that brings both back. */
@Composable
private fun FoldSlip(area: Position, edit: BarEdit, modifier: Modifier) {
    val c = Bento.colors
    val requester = remember { FocusRequester() }
    LaunchedEffect(edit.focusFold) {
        if (!edit.focusFold) return@LaunchedEffect
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        edit.focusFold = false
    }
    Row(modifier.fillMaxWidth().padding(start = ROW_INSET, end = ROW_INSET, bottom = Space.xs).height(Target.row).background(c.sunk, ROW_SHAPE)
        .padding(start = 14.dp, end = Space.xs).testTag("fold"), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.areas_folded, stringResource(areaRes(area))), Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = Bento.type.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BentoButton(stringResource(R.string.bar_undo), { edit.undo() }, Modifier.focusRequester(requester).onFocusChanged { edit.slipFocused = it.hasFocus },
            kind = ButtonKind.TEXT)
    }
}

/** The bar has no room for [area] right now: who takes the free space first, once, above its rows. */
@Composable
private fun NoRoom(area: Position, cfg: BarConfig, modifier: Modifier) {
    val ahead = roomAhead(cfg, area).map { stringResource(areaRes(it)) }
    val name = stringResource(areaRes(area))
    val text = if (ahead.size > 1) stringResource(R.string.areas_no_room_two, ahead[0], ahead[1], name)
        else stringResource(R.string.areas_no_room_one, ahead.firstOrNull().orEmpty(), name)
    NoticeLine(text, modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = Space.xs).semantics { liveRegion = LiveRegionMode.Polite }.testTag("no-room"))
}

/** The line between the parts: what the lower one is, in the hidden mode's words, and how its rows come to show. */
@Composable
private fun PartLine(mode: HiddenMode, modifier: Modifier) {
    val c = Bento.colors
    val title = stringResource(if (mode == HiddenMode.SHOW_ALL) R.string.section_when_active else R.string.section_hidden)
    val help = stringResource(when (mode) {
        HiddenMode.SHOW_ALL -> R.string.section_hidden_reveal_show_all
        HiddenMode.CLICK -> R.string.section_hidden_reveal_chevron
        HiddenMode.HOVER -> R.string.section_hidden_reveal_both
    })
    val spoken = title + ". " + help.replace("‹", stringResource(R.string.chevron_spoken))
    // The words take what they need and the hairline the rest, to the card's edge: weights would split the room instead.
    Layout({
        Text(title, style = Bento.type.help.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
        Text(help, style = Bento.type.help, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.background(c.line))
    }, modifier.testTag("part-line").fillMaxWidth().padding(horizontal = Space.xl).clearAndSetSemantics { heading(); contentDescription = spoken }) { m, cs ->
        val gap = Space.s.roundToPx()
        val lineGap = Space.m.roundToPx()
        val name = m[0].measure(Constraints(maxWidth = cs.maxWidth))
        val words = m[1].measure(Constraints(maxWidth = (cs.maxWidth - name.width - gap - lineGap - PART_LINE_MIN.roundToPx()).coerceAtLeast(0)))
        val lineAt = name.width + gap + words.width + lineGap
        val line = m[2].measure(Constraints.fixed((cs.maxWidth - lineAt).coerceAtLeast(0), 1.dp.roundToPx()))
        val height = maxOf(name.height, words.height, PART_LINE_HEIGHT.roundToPx())
        layout(cs.maxWidth, height) {
            name.placeRelative(0, (height - name.height) / 2)
            words.placeRelative(name.width + gap, (height - words.height) / 2)
            line.placeRelative(lineAt, (height - line.height) / 2)
        }
    }
}

private val PART_LINE_HEIGHT = 32.dp
private val PART_LINE_MIN = 24.dp

/** The dragged row's place: an opening the size of the row, where it would land. */
@Composable
private fun DropOpening(id: String, reorder: Reorder, modifier: Modifier) {
    val tint = Bento.colors.accent
    Box(modifier.onGloballyPositioned { reorder.rows[id] = it }.animatePlacement().fillMaxWidth().padding(horizontal = ROW_INSET)
        .height(with(LocalDensity.current) { reorder.rowHeight.toDp() }).clip(ROW_SHAPE)
        .background(tint.copy(alpha = 0.08f)).border(1.dp, tint.copy(alpha = 0.35f), ROW_SHAPE))
}

/** A removed row's place: what was removed, as the library names it, and the one Undo. It closes with the next change. */
@Composable
private fun SlipRow(slip: Slip, edit: BarEdit, modifier: Modifier) {
    val c = Bento.colors
    val res = LocalResources.current
    Row(modifier.animatePlacement().fillMaxWidth().padding(horizontal = ROW_INSET).height(Target.row).background(c.sunk, ROW_SHAPE)
        .padding(start = Space.l, end = Space.xs).testTag("slip"), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.bar_removed, removedName(res, slip.item)), Modifier.weight(1f), style = Bento.type.small, color = c.ink2,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        BentoButton(stringResource(R.string.bar_undo), { edit.undo() }, Modifier.onFocusChanged { edit.slipFocused = it.hasFocus }, kind = ButtonKind.TEXT)
    }
}

/**
 * What a removed item is called, as the library names it: a setup by its type and its line's words ("Clock · Tokyo"),
 * an item that carried nothing by its type alone.
 */
internal fun removedName(res: android.content.res.Resources, item: ItemConfig): String {
    val type = Items.of(item.type)?.takeIf { item.carriesSetup(it) } ?: return itemName(res, item)
    return res.getString(R.string.library_setup, res.getString(type.titleRes), setupText(res, item, type))
}

@Composable
private fun Grip(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 28.dp, height = 36.dp), contentAlignment = Alignment.Center) { SymIcon(Sym.DRAG_INDICATOR, size = 20.sp, color = color) }
}

/** What a row says at its end about the last change: the library just put it there, or it moved as its lane closed. */
private enum class Mark { ADDED, MOVED }

/**
 * One item's row: grip, glyph, name, and what it says or waits for. A click (on release, with no movement) selects it;
 * the keys act on the focused row; a screen reader has each command as an action, by the same rules as the controls,
 * and with several [lanes] on screen "Move to" for each of the others.
 */
@Composable
private fun ItemRow(item: ItemConfig, cfg: BarConfig, state: ItemState?, selected: Boolean, overflow: Boolean, mark: Mark?, calendar: Boolean,
                    edit: BarEdit, reorder: Reorder, lanes: List<Position>, stacked: Boolean, modifier: Modifier) {
    val c = Bento.colors
    val res = LocalResources.current
    val id = item.id
    val interaction = remember { MutableInteractionSource() }
    val requester = remember { FocusRequester() }
    val bring = remember { BringIntoViewRequester() }
    val follow = edit.follow
    // Focus follows a moved row by its id: asked for once, after the list has settled, then forgotten.
    LaunchedEffect(follow) {
        if (follow?.id != id) return@LaunchedEffect
        withFrameNanos { }
        if (follow.focus) runCatching { requester.requestFocus() }
        runCatching { bring.bringIntoView() }
        if (edit.follow == follow) edit.follow = null
    }
    val grab = remember { PointerIcon(android.view.PointerIcon.TYPE_GRAB) }
    val pinned = item.type == Store.PINNED_LEFT
    val chevron = cfg.hiddenMode != HiddenMode.SHOW_ALL
    val up = stringResource(R.string.row_move_up)
    val down = stringResource(R.string.row_move_down)
    val always = stringResource(R.string.row_show_always)
    val below = stringResource(if (chevron) R.string.row_put_behind else R.string.row_show_when_active)
    val remove = stringResource(R.string.detail_remove)
    val actions = remember(cfg, id, up, down, always, below, remove, lanes) {
        buildList {
            if (cfg.stepped(id, down = false) is Step.Moved) add(CustomAccessibilityAction(up) { edit.step(id, down = false); true })
            if (cfg.stepped(id, down = true) is Step.Moved) add(CustomAccessibilityAction(down) { edit.step(id, down = true); true })
            val standing = cfg.standing(item)
            if (standing != Standing.ALWAYS) add(CustomAccessibilityAction(always) { edit.always(id); true })
            else if (cfg.canGoBelow(item)) add(CustomAccessibilityAction(below) { edit.below(id); true })
            if (lanes.size > 1) lanes.filter { it != item.area }.forEach { to ->
                add(CustomAccessibilityAction(res.getString(R.string.areas_move_to, res.getString(areaRes(to)))) { edit.area(id, to); true })
            }
            add(CustomAccessibilityAction(remove) { edit.remove(id); true })
        }
    }
    // With several lanes a row that doesn't fit says only that, and behind which arrow: Left's points right.
    val behind = if (overflow && lanes.size > 1) stringResource(R.string.row_overflow_behind, if (item.area == Position.LEFT) "›" else "‹") else null
    RowBody(item, state, overflow && behind == null, behind, calendar, cfg,
        modifier.onGloballyPositioned { reorder.rows[id] = it; reorder.grabAreas[id] = it }.animatePlacement()
            .padding(horizontal = ROW_INSET)
            .bringIntoViewRequester(bring).focusRequester(requester)
            .onFocusChanged { if (it.isFocused) edit.focusedRow = id else if (edit.focusedRow == id) edit.focusedRow = null }
            .states(interaction, ground = !selected)
            .background(if (selected) c.soft else Color.Transparent, ROW_SHAPE)
            .then(if (selected) Modifier.border(1.5.dp, c.accent, ROW_SHAPE) else Modifier)
            .clickable(interaction, indication = null) { edit.select(id) }
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { this.selected = selected; customActions = actions }
            .testTag("row:$id"),
        stacked = stacked, mark = mark,
        grip = { if (pinned) Spacer(Modifier.width(28.dp)) else Grip(c.ink3, Modifier.onGloballyPositioned { reorder.handles[id] = it }.pointerHoverIcon(grab).testTag("grip")) })
}

/**
 * What a row draws, for the list and for the copy that floats while it is dragged: on one line, or [stacked], the name
 * over what it says (the narrow window, and a lane). [behind]: all it says while it doesn't fit and the areas are on
 * screen, as a warning.
 */
@Composable
private fun RowBody(item: ItemConfig, state: ItemState?, overflow: Boolean, behind: String?, calendar: Boolean, cfg: BarConfig, modifier: Modifier,
                    stacked: Boolean, mark: Mark? = null, grip: @Composable () -> Unit) {
    val c = Bento.colors
    val res = LocalResources.current
    val type = Items.of(item.type)
    val needs = type != null && android.Manifest.permission.READ_CALENDAR in type.permissions && !calendar
    val detail = if (needs) stringResource(R.string.row_needs_calendar) else behind ?: listOfNotNull(
        if (overflow) stringResource(R.string.row_overflow) else null,
        state?.text?.takeIf { it.isNotBlank() && type != null },
        if (type != null && cfg.partOf(item) == Part.LOWER && item.whenActive) type.trigger?.short(item) ?: stringResource(R.string.row_when_active) else null,
        if (type == null || type.iconOnly) null else when (item.display) {
            Display.ICON -> stringResource(R.string.row_icon_only)
            Display.TEXT -> stringResource(R.string.row_text_only)
            else -> null
        },
    ).joinToString(" · ")
    val warn = needs || behind != null
    Row(modifier.fillMaxWidth().then(if (stacked) Modifier.heightIn(min = Target.row) else Modifier.height(Target.row)).padding(start = 6.dp, end = Space.m), verticalAlignment = Alignment.CenterVertically) {
        grip()
        SymIcon(type?.icon ?: Sym.UPDATE, size = 18.sp, color = c.ink)
        Spacer(Modifier.width(10.dp))
        val name = itemName(res, item)
        // In a lane the words about fitting are long for the column: they take a second line, never an ellipsis.
        val lines = if (behind != null && !needs) 2 else 1
        if (stacked) Column(Modifier.weight(1f).padding(vertical = if (lines > 1) 6.dp else 0.dp)) {
            Text(name, style = Bento.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) Detail(detail, warn, lines = lines)
        } else {
            Text(name, Modifier.width(188.dp), style = Bento.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Detail(detail, warn, Modifier.weight(1f))
        }
        if (mark != null) Text(stringResource(if (mark == Mark.ADDED) R.string.add_added else R.string.areas_moved),
            Modifier.padding(start = Space.s).testTag(if (mark == Mark.ADDED) "added-mark" else "moved-mark"),
            style = Bento.type.help.copy(fontWeight = FontWeight.SemiBold), color = c.accent, maxLines = 1)
    }
}

@Composable
private fun Detail(text: String, warn: Boolean, modifier: Modifier = Modifier, lines: Int = 1) {
    val c = Bento.colors
    Row(modifier, verticalAlignment = if (lines > 1) Alignment.Top else Alignment.CenterVertically) {
        if (warn) { SymIcon(Sym.WARNING, size = 14.sp, color = c.warnInk); Spacer(Modifier.width(Space.xs)) }
        Text(text, style = Bento.type.small.tabular, color = if (warn) c.warnInk else c.ink2, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/** A strip that only draws: the chip the dragged row becomes over the preview. */
private object ChipOnly : StripEvents {
    override fun click(item: ItemConfig, at: android.graphics.Rect) {}
    override fun context(item: ItemConfig, at: android.graphics.Rect) {}
    override fun scroll(item: ItemConfig, steps: Int) {}
    override fun chevron(at: android.graphics.Rect) {}
    override fun chevronContext(at: android.graphics.Rect) {}
    override fun hover(inside: Boolean) {}
    override fun placed(id: String, at: android.graphics.Rect) {}
    override val slidable get() = false
    override val draggable get() = false
}

/**
 * What is carried, drawn over the whole page under the pointer: the row as the list draws it, raised, and over the
 * preview the item's chip as the strip draws it. It turns from one into the other by its size and its shape in 120 ms,
 * never by a fade: one of the two is drawn at a time, cut to the shape between them.
 */
@Composable
internal fun DragCopy(reorder: Reorder, cfg: BarConfig, states: Map<String, ItemState>, several: Boolean) {
    val carried = reorder.carried ?: return
    val c = Bento.colors
    val item = remember(carried, cfg) {
        if (carried.from == From.TYPE) Items.of(carried.type)?.fresh(carried.id, cfg.homeArea()) else cfg.items.firstOrNull { it.id == carried.id }
    } ?: return
    val state = states[item.id] ?: remember(item) { Ticker.placeholder(item) }
    val setup by Setup.state.collectAsState()
    val narrow = LocalWindowLayout.current.kind == WindowClass.NARROW
    val look = reorder.look
    val chip by animateFloatAsState(if (reorder.inPreview && look != null) 1f else 0f, Motion.spec(tween(120)), label = "chip")
    var up by remember(carried) { mutableStateOf(false) }
    LaunchedEffect(carried) { up = true }
    val raised by animateFloatAsState(if (up) 1f else 0f, Motion.spec(tween(120)), label = "raised")
    val ground = if (reorder.ground == Color.Unspecified) c.raised else reorder.ground
    Layout({
        RowBody(item, state, false, null, setup.calendar, cfg, Modifier, stacked = narrow || several, grip = { Grip(c.accent) })
        Box(Modifier.padding(horizontal = 10.dp)) {
            if (look != null) Strip(listOf(StripEntry(item, state)), emptyList(), showChevron = false, chevronAlways = false, chevronReservePx = 0,
                expanded = false, chevronOnLeft = false, look = look.copy(pill = Pill.NONE), maxWidthPx = 4000, heightDp = 36.dp, events = ChipOnly)
        }
    }, Modifier.offset { reorder.copyAt(chip).round() }.testTag(if (chip < 0.5f) "drag-row" else "drag-chip")
        .graphicsLayer {
            shadowElevation = 8.dp.toPx() * raised
            shape = RoundedCornerShape(lerp(Radius.medium, (size.height / 2).toDp(), chip))
            clip = true
        }
        .background(lerp(c.raised, ground, chip)).clearAndSetSemantics { }) { measurables, _ ->
        val row = measurables[0].measure(Constraints.fixedWidth(reorder.copyWidth.roundToInt().coerceAtLeast(1)))
        val pill = measurables[1].measure(Constraints())
        val w = (row.width + (pill.width - row.width) * chip).roundToInt()
        val h = (row.height + (pill.height - row.height) * chip).roundToInt()
        layout(w, h) { if (chip < 0.5f) row.place(0, 0) else pill.place((w - pill.width) / 2, (h - pill.height) / 2) }
    }
}

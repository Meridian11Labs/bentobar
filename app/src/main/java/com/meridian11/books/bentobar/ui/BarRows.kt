package com.meridian11.books.bentobar.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.BarOverflow
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
import com.meridian11.books.bentobar.data.listAreas
import com.meridian11.books.bentobar.data.partOf
import com.meridian11.books.bentobar.data.parts
import com.meridian11.books.bentobar.data.standing
import com.meridian11.books.bentobar.data.stepped
import com.meridian11.books.bentobar.data.undone
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.util.Sym
import com.meridian11.books.bentobar.util.SymIcon
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

/** One line of the bar's list. A row's [key] is its item's id, so a row that changes part or area is the same row. */
sealed interface Entry {
    val key: Any

    /** The list's head, with one area in use: "In the bar · Left to right, as they appear". */
    data class ListHead(val area: Position) : Entry { override val key: Any get() = "head" to area }
    /** An area's name, with several in use; [first] has no line above it. */
    data class AreaHead(val area: Position, val first: Boolean) : Entry { override val key: Any get() = "area" to area }
    /** The line between an area's two parts. */
    data class PartLine(val area: Position) : Entry { override val key: Any get() = "line" to area }
    data class Row(val item: ItemConfig, val zone: Zone) : Entry { override val key: Any get() = item.id }
    data class Removed(val slip: Slip) : Entry { override val key: Any get() = "slip" to slip.item.id }
    /** Nothing is in the bar. */
    data object Empty : Entry { override val key: Any get() = "empty" to 0 }
}

/**
 * The bar's one list, top to bottom: for each area with rows (Left, Middle, Right) its head, its upper part, the line
 * and its lower part. With one area the line is always there (it says what the lower part is, and is where a row is
 * dropped to put it there); with several, only for a lower part that has rows, or while a row is [dragging]. A [slip]
 * stands where its row stood.
 */
fun listEntries(cfg: BarConfig, slip: Slip?, dragging: Boolean): List<Entry> {
    val areas = listOf(Position.LEFT, Position.CENTER, Position.RIGHT).filter { it in cfg.listAreas() || it == slip?.zone?.area }
    val several = areas.size > 1
    if (slip == null && cfg.items.none { cfg.partOf(it) != null }) return listOf(Entry.ListHead(areas.first()), Entry.Empty)
    return buildList {
        areas.forEachIndexed { i, area ->
            add(if (several) Entry.AreaHead(area, first = i == 0) else Entry.ListHead(area))
            val parts = cfg.parts(area)
            for (part in Part.entries) {
                val zone = Zone(area, part)
                val rows: List<Entry> = parts[part].map { Entry.Row(it, zone) }
                val here = slip?.takeIf { it.zone == zone }
                if (part == Part.LOWER && (!several || rows.isNotEmpty() || here != null || dragging)) add(Entry.PartLine(area))
                if (here == null) addAll(rows)
                else addAll(rows.toMutableList().apply { add(here.index.coerceIn(0, size), Entry.Removed(here)) })
            }
        }
    }
}

private val ROW_SHAPE = RoundedCornerShape(Radius.medium)
private val ROW_INSET = 12.dp

/**
 * The bar's card: the one list. A click selects a row, a click on the card's empty part lets the selection go, and a
 * row is dragged to a new place by [reorder] (the gesture is on the container, a copy of the row floats above it).
 */
@Composable
internal fun BarList(cfg: BarConfig, states: Map<String, ItemState>, selected: String?, edit: BarEdit, reorder: Reorder, modifier: Modifier = Modifier) {
    val c = Bento.colors
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val kept by Undo.last.collectAsState()
    val setup by Setup.state.collectAsState()
    val overflow by BarOverflow.ids.collectAsState()
    SideEffect { reorder.config = cfg }
    // The Store has caught up with the drop: the row is where it landed.
    LaunchedEffect(cfg) { reorder.pending = null }
    AutoScroll(reorder, scroll)
    val shown = reorder.preview(cfg)
    val dragging = reorder.dragging
    val entries = remember(shown, kept, dragging != null) { listEntries(shown, slipOf(kept, shown), dragging != null) }
    val grabbing = remember { PointerIcon(android.view.PointerIcon.TYPE_GRABBING) }
    Column(modifier.clip(RoundedCornerShape(Radius.large)).background(c.panel)
        .pointerInput(edit) { detectTapGestures { edit.letGo() } }
        .verticalScroll(scroll).padding(vertical = Space.m).testTag("bar-list")) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned { reorder.container = it }.reorderable(reorder, scope)
            .pointerHoverIcon(if (dragging != null) grabbing else PointerIcon.Default, overrideDescendants = dragging != null)) {
            Column {
                // One call site for every line: a row that moves to another part or area keeps its node, and its focus.
                entries.forEach { e ->
                    key(e.key) {
                        when (e) {
                            is Entry.ListHead -> ListHead(Modifier.onGloballyPositioned { reorder.headings[Zone(e.area, Part.UPPER)] = it })
                            is Entry.AreaHead -> AreaHead(e, Modifier.onGloballyPositioned { reorder.headings[Zone(e.area, Part.UPPER)] = it })
                            is Entry.PartLine -> PartLine(cfg.hiddenMode, Modifier.onGloballyPositioned { reorder.headings[Zone(e.area, Part.LOWER)] = it })
                            is Entry.Row ->
                                if (e.item.id == dragging) DropOpening(e.item.id, reorder)
                                else ItemRow(e.item, shown, states[e.item.id], e.item.id == selected, e.item.id in overflow, setup.calendar, edit, reorder)
                            is Entry.Removed -> SlipRow(e.slip, edit)
                            Entry.Empty -> Text(stringResource(R.string.bar_empty), Modifier.padding(horizontal = Space.xl, vertical = Space.s),
                                style = Bento.type.small, color = c.ink2)
                        }
                    }
                }
            }
            val lifted = dragging?.let { id -> cfg.items.firstOrNull { it.id == id } }
            if (lifted != null) Box(Modifier.offset { IntOffset(reorder.rowLeft.roundToInt(), reorder.ghostTop.roundToInt()) }
                .width(with(density) { reorder.rowWidth.toDp() }).clearAndSetSemantics { }) {
                RowBody(lifted, states[lifted.id], lifted.id in overflow, setup.calendar, cfg,
                    Modifier.padding(horizontal = ROW_INSET).shadow(8.dp, ROW_SHAPE).clip(ROW_SHAPE).background(c.raised), grip = { Grip(c.accent) })
            }
        }
    }
}

/** While a row is dragged near the list's upper or lower edge, the list scrolls under it, and the opening follows. */
@Composable
private fun AutoScroll(reorder: Reorder, scroll: ScrollState) {
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val maxStep = with(density) { 14.dp.toPx() }
    LaunchedEffect(reorder.dragging) {
        if (reorder.dragging == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val v = reorder.edgeSpeed(edge, maxStep)
            if (v != 0f) scroll.scrollBy(v)
            reorder.sync()
        }
    }
}

@Composable
private fun ListHead(modifier: Modifier) {
    val c = Bento.colors
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = Space.xl).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.section_shown), style = Bento.type.strong, color = c.ink)
        Text(" · " + stringResource(R.string.section_shown_help), style = Bento.type.help, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AreaHead(e: Entry.AreaHead, modifier: Modifier) {
    val c = Bento.colors
    Column(modifier.fillMaxWidth()) {
        if (!e.first) Box(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.s).height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = Space.xl).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(areaRes(e.area)), style = Bento.type.strong, color = c.ink)
        }
    }
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
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = Space.xl)
        .clearAndSetSemantics { heading(); contentDescription = spoken }.testTag("part-line"), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Bento.type.help.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
        Spacer(Modifier.width(Space.s))
        Text(help, Modifier.weight(3f, fill = false), style = Bento.type.help, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(Space.m))
        Box(Modifier.weight(1f).height(1.dp).background(c.line))
    }
}

/** The dragged row's place: an opening the size of the row, where it would land. */
@Composable
private fun DropOpening(id: String, reorder: Reorder) {
    val tint = Bento.colors.accent
    Box(Modifier.onGloballyPositioned { reorder.rows[id] = it }.animatePlacement().fillMaxWidth().padding(horizontal = ROW_INSET)
        .height(with(LocalDensity.current) { reorder.rowHeight.toDp() }).clip(ROW_SHAPE)
        .background(tint.copy(alpha = 0.08f)).border(1.dp, tint.copy(alpha = 0.35f), ROW_SHAPE))
}

/** A removed row's place: what was removed, as the library names it, and the one Undo. It closes with the next change. */
@Composable
private fun SlipRow(slip: Slip, edit: BarEdit) {
    val c = Bento.colors
    val res = LocalResources.current
    Row(Modifier.animatePlacement().fillMaxWidth().padding(horizontal = ROW_INSET).height(Target.row).background(c.sunk, ROW_SHAPE)
        .padding(start = Space.l, end = Space.xs).testTag("slip"), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.bar_removed, removedName(res, slip.item)), Modifier.weight(1f), style = Bento.type.small, color = c.ink2,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        BentoButton(stringResource(R.string.bar_undo), { edit.undo() }, Modifier.onFocusChanged { edit.slipFocused = it.hasFocus }, kind = ButtonKind.TEXT)
    }
}

/** What a removed item is called, as the library will name its line. */
internal fun removedName(res: android.content.res.Resources, item: ItemConfig): String = itemName(res, item)

@Composable
private fun Grip(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 28.dp, height = 36.dp), contentAlignment = Alignment.Center) { SymIcon(Sym.DRAG_INDICATOR, size = 20.sp, color = color) }
}

/**
 * One item's row: grip, glyph, name, and what it says or waits for. A click (on release, with no movement) selects it;
 * the keys act on the focused row; a screen reader has each command as an action, by the same rules as the controls.
 */
@Composable
private fun ItemRow(item: ItemConfig, cfg: BarConfig, state: ItemState?, selected: Boolean, overflow: Boolean, calendar: Boolean, edit: BarEdit, reorder: Reorder) {
    val c = Bento.colors
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
    val actions = remember(cfg, id, up, down, always, below, remove) {
        buildList {
            if (cfg.stepped(id, down = false) is Step.Moved) add(CustomAccessibilityAction(up) { edit.step(id, down = false); true })
            if (cfg.stepped(id, down = true) is Step.Moved) add(CustomAccessibilityAction(down) { edit.step(id, down = true); true })
            val standing = cfg.standing(item)
            if (standing != Standing.ALWAYS) add(CustomAccessibilityAction(always) { edit.always(id); true })
            else if (cfg.canGoBelow(item)) add(CustomAccessibilityAction(below) { edit.below(id); true })
            add(CustomAccessibilityAction(remove) { edit.remove(id); true })
        }
    }
    RowBody(item, state, overflow, calendar, cfg,
        Modifier.onGloballyPositioned { reorder.rows[id] = it; reorder.grabAreas[id] = it }.animatePlacement()
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
        grip = { if (pinned) Spacer(Modifier.width(28.dp)) else Grip(c.ink3, Modifier.onGloballyPositioned { reorder.handles[id] = it }.pointerHoverIcon(grab)) })
}

/** What a row draws, for the list and for the copy that floats while it is dragged. */
@Composable
private fun RowBody(item: ItemConfig, state: ItemState?, overflow: Boolean, calendar: Boolean, cfg: BarConfig, modifier: Modifier, grip: @Composable () -> Unit) {
    val c = Bento.colors
    val res = LocalResources.current
    val type = Items.of(item.type)
    val narrow = LocalWindowLayout.current.kind == WindowClass.NARROW
    val needs = type != null && android.Manifest.permission.READ_CALENDAR in type.permissions && !calendar
    val detail = if (needs) stringResource(R.string.row_needs_calendar) else listOfNotNull(
        if (overflow) stringResource(R.string.row_overflow) else null,
        state?.text?.takeIf { it.isNotBlank() && type != null },
        if (type != null && cfg.partOf(item) == Part.LOWER && item.whenActive) type.trigger?.short(item) ?: stringResource(R.string.row_when_active) else null,
        if (type == null || type.iconOnly) null else when (item.display) {
            Display.ICON -> stringResource(R.string.row_icon_only)
            Display.TEXT -> stringResource(R.string.row_text_only)
            else -> null
        },
    ).joinToString(" · ")
    Row(modifier.fillMaxWidth().height(if (narrow) 56.dp else Target.row).padding(start = 6.dp, end = Space.m), verticalAlignment = Alignment.CenterVertically) {
        grip()
        SymIcon(type?.icon ?: Sym.UPDATE, size = 18.sp, color = c.ink)
        Spacer(Modifier.width(10.dp))
        val name = itemName(res, item)
        if (narrow) Column(Modifier.weight(1f)) {
            Text(name, style = Bento.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) Detail(detail, needs)
        } else {
            Text(name, Modifier.width(188.dp), style = Bento.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Detail(detail, needs, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Detail(text: String, warn: Boolean, modifier: Modifier = Modifier) {
    val c = Bento.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (warn) { SymIcon(Sym.WARNING, size = 14.sp, color = c.warnInk); Spacer(Modifier.width(Space.xs)) }
        Text(text, style = Bento.type.small.tabular, color = if (warn) c.warnInk else c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

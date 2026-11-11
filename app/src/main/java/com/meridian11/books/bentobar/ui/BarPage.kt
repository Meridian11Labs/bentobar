package com.meridian11.books.bentobar.ui

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Step
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.broughtBack
import com.meridian11.books.bentobar.data.hide
import com.meridian11.books.bentobar.data.partOf
import com.meridian11.books.bentobar.data.parts
import com.meridian11.books.bentobar.data.putInArea
import com.meridian11.books.bentobar.data.removed
import com.meridian11.books.bentobar.data.setWhenActive
import com.meridian11.books.bentobar.data.show
import com.meridian11.books.bentobar.data.stepped
import com.meridian11.books.bentobar.data.without
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.items.ItemType
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.items.Ticker
import com.meridian11.books.bentobar.util.Sym
import kotlinx.coroutines.delay

/** What the panel shows: the bar's list (below the width that has room for both), the library, or the selected item's options. */
enum class PanelSide { BAR, ADD, OPTIONS }

/** A row the list goes to after a change: scrolled into view, and focused if the row it stands for had the focus. */
internal data class Follow(val id: String, val focus: Boolean, val serial: Int)

/** The Bar page's keys for a press that no control took (nothing in the window has the focus): the window hands it here. */
internal object BarKeys {
    var handler: ((android.view.KeyEvent) -> Boolean)? = null
}

/**
 * The Bar page's commands, one for each thing a click, a key or a screen reader's action can do to the bar, so the three
 * can't differ. Each change to the layout is one step of [Undo] through `LayoutIntents`; this class writes no section,
 * rule or area itself. It also holds what the page remembers between frames: the panel's side, the library's filter,
 * the row to follow, and what was last said.
 */
@Stable
internal class BarEdit(private val res: Resources, side: MutableState<PanelSide>, filter: MutableState<String> = mutableStateOf("")) {
    var onSelect: (String?) -> Unit = {}
    /** The window has room for the list and the panel side by side. */
    var wide = true
    /** The selected item, if it is in the bar. */
    var selected: String? = null
    var side by side
    /** The side the panel shows right now, which is [side] where that can be shown. */
    var showing = PanelSide.ADD
    /** What the library's filter holds. */
    var filter by filter
    /** The filter is to take the keyboard (Ctrl+F, a letter typed with no field in focus). */
    var focusFilter by mutableStateOf(false)
    /** What the library goes to after a change. */
    var seek by mutableStateOf<Seek?>(null)
    var focusedRow: String? = null
    var slipFocused = false
    var follow by mutableStateOf<Follow?>(null)
    /** Why a key or a drop did nothing, for the line under the preview. */
    var said by mutableStateOf<String?>(null)
    /** What a change came to, for a screen reader: "CPU load, 5th of 7 in the bar". */
    var announced by mutableStateOf("")
    private var serial = 0

    private fun home() = if (wide) PanelSide.ADD else PanelSide.BAR
    private fun item(id: String) = Store.config.value.items.firstOrNull { it.id == id }

    /** A click on an item, in the list or the preview: the panel turns to its options. */
    fun select(id: String) {
        said = null
        onSelect(id)
        side = PanelSide.OPTIONS
    }

    /** Esc, or a click on an empty part of the bar's card: the item is let go and the panel turns back. */
    fun letGo() {
        said = null
        onSelect(null)
        side = home()
    }

    /** "Add items": the library, with the item still selected. */
    fun toLibrary() { side = PanelSide.ADD }

    /** Ctrl+F: the library, and the keyboard in its filter. The item stays selected. */
    fun find() {
        toLibrary()
        focusFilter = true
    }

    /**
     * The + on a type: a fresh item of it, in the area in use (the seam for a chosen area), under the line if the type
     * comes waiting on its rule. The bar's list goes to the new row; the keyboard stays on the +.
     */
    fun add(type: ItemType) {
        said = null
        val before = Store.config.value
        val id = Store.addFresh(type)
        if (Undo.note(before, added = id)) moved(id, focus = false)
    }

    /**
     * The + on a remembered setup's line: the item back in the bar as it was, in the area in use. Its line is gone, so
     * the keyboard, if the + had it ([focus]), goes to its type's +.
     */
    fun addSetup(id: String, focus: Boolean) {
        said = null
        val item = item(id) ?: return
        if (!Undo.change(added = id) { it.broughtBack(id) }) return
        if (focus) seek = Seek(Seek.type(item.type), true, ++serial)
        moved(id, focus = false)
    }

    /** Forget on a line: the setup is gone, and until the next change its place says so, with the Undo the keyboard goes to. */
    fun forget(id: String, focus: Boolean) {
        said = null
        val item = item(id)?.takeIf { it.section == Section.OFF } ?: return
        if (!Undo.change { it.without(id) }) return
        if (focus) seek = Seek(Seek.UNDO, true, ++serial)
        announced = res.getString(R.string.library_forgot, removedName(res, item))
    }

    fun always(id: String) = change(id) { it.show(id) }

    /** Visibility's second part: "Only when active" by default, "Behind ‹" with the arrow. */
    fun below(id: String) = change(id) { c -> if (c.hiddenMode == HiddenMode.SHOW_ALL) c.setWhenActive(id, true) else c.hide(id) }

    /** Behind ‹: whether the item pops out by its rule. */
    fun popsOut(id: String, on: Boolean) {
        said = null
        Undo.change { it.setWhenActive(id, on) }
    }

    fun area(id: String, area: Position) = change(id) { it.putInArea(id, area) }

    /** Move up or down by one row, across the part line where the item may; where it may not, the reason is [said]. */
    fun step(id: String, down: Boolean) {
        val item = item(id) ?: return
        when (val step = Store.config.value.stepped(id, down)) {
            is Step.Moved -> change(id) { c -> (c.stepped(id, down) as? Step.Moved)?.config ?: c }
            is Step.Stays -> said = when (step.why) {
                Step.Why.NO_RULE -> res.getString(R.string.visibility_no_rule_drop, itemName(res, item))
                Step.Why.PINNED -> res.getString(R.string.order_pinned_said, res.getString(areaRes(item.area)))
            }
            Step.End -> Unit
        }
    }

    /** A dragged row was carried under a line it can't go under. */
    fun refused(id: String) {
        val item = item(id) ?: return
        said = res.getString(R.string.visibility_no_rule_drop, itemName(res, item))
    }

    /**
     * Remove from the bar. The focus, if the row had it, goes to the row that takes its place (the one before, at a
     * part's end). The library goes to where the item now waits, its line, or to its type if it carried nothing; removed
     * in its options, the panel turns to the library for that, with the filter emptied so that the line is there.
     */
    fun remove(id: String) {
        val cfg = Store.config.value
        val item = item(id) ?: return
        val part = cfg.partOf(item) ?: return
        val rows = cfg.parts(item.area)[part]
        val at = rows.indexOfFirst { it.id == id }
        val next = (rows.getOrNull(at + 1) ?: rows.getOrNull(at - 1))?.id
        val focus = focusedRow == id
        said = null
        if (!Undo.change(removed = id) { it.removed(id) }) return
        if (selected == id) {
            onSelect(null)
            side = PanelSide.ADD
            filter = ""
        }
        seek = Seek(if (item(id) != null) Seek.line(id) else Seek.type(item.type), false, ++serial)
        if (focus && next != null) follow = Follow(next, true, ++serial)
        announced = res.getString(R.string.bar_removed, removedName(res, item))
    }

    /** A copy right after the item, selected. */
    fun duplicate(id: String) {
        val before = Store.config.value
        val item = item(id) ?: return
        if (Items.of(item.type) == null || before.partOf(item) == null) return
        val focus = focusedRow == id
        val copy = Store.duplicate(item)
        if (!Undo.note(before)) return
        select(copy)
        moved(copy, focus)
    }

    /**
     * Ctrl+Z, Undo in a removed row's place, and the library's Undo. A row that comes back is followed; the library's
     * Undo is gone with what it took back, so the keyboard, if it had it ([focus]), goes to the type's + after an add
     * and to the line that is back after Forget.
     */
    fun undo(focus: Boolean = false): Boolean {
        said = null
        val kept = Undo.last.value
        val back = kept?.removed
        val row = focusedRow != null || slipFocused
        val to = if (!focus) null else kept?.added?.let(::item)?.let { Seek.type(it.type) }
            ?: Undo.forgotten(kept, Store.config.value)?.let { Seek.line(it.id) }
        if (!Undo.undo()) return false
        if (back != null) moved(back, row)
        if (to != null) seek = Seek(to, true, ++serial)
        return true
    }

    private fun change(id: String, step: (BarConfig) -> BarConfig) {
        said = null
        val focus = focusedRow == id
        if (Undo.change(step = step)) moved(id, focus)
    }

    private fun moved(id: String, focus: Boolean) {
        follow = Follow(id, focus, ++serial)
        val item = item(id) ?: return
        announced = orderWords(res, Store.config.value, item)?.let { res.getString(R.string.row_moved, itemName(res, item), it) }.orEmpty()
    }

    /**
     * The page's keys, on the focused row or else the selected item. Esc does one thing, the nearest first: it cancels a
     * drag, empties the filter of the library on screen, or lets the item go. Ctrl+F finds from anywhere. While text is
     * being typed ([typing]) every other key is the field's; with no field in focus a letter or a digit typed beside the
     * library starts its filter.
     */
    fun key(e: KeyEvent, typing: Boolean, reorder: Reorder): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (e.key == Key.Escape) {
            if (reorder.dragging != null) { reorder.cancel(); return true }
            if (showing == PanelSide.ADD && filter.isNotEmpty()) { filter = ""; return true }
            if (typing || selected == null) return false
            letGo()
            return true
        }
        // No field has a use for Ctrl+F, so it finds from inside one too.
        if (e.isCtrlPressed && !e.isAltPressed && e.key == Key.F) { find(); return true }
        if (typing) return false
        val target = focusedRow ?: selected
        return when {
            e.isAltPressed && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> target?.let { step(it, down = e.key == Key.DirectionDown) } != null
            e.isAltPressed -> false
            e.isCtrlPressed && e.key == Key.Z -> { undo(); true }
            e.isCtrlPressed && e.key == Key.D -> target?.let { duplicate(it) } != null
            e.isCtrlPressed || e.isMetaPressed -> false
            e.key == Key.Delete || e.key == Key.Backspace -> target?.let { remove(it) } != null
            showing == PanelSide.ADD && Character.isLetterOrDigit(e.nativeKeyEvent.unicodeChar) -> {
                filter += String(Character.toChars(e.nativeKeyEvent.unicodeChar))
                focusFilter = true
                true
            }
            else -> false
        }
    }
}

private val CARD = RoundedCornerShape(Radius.large)
private const val TURN_MS = 180
/** The switch of three parts where the page is one wide pane: as wide as it needs, not as the page. */
private val SWITCH_MID = 452.dp

/**
 * The Bar page: the preview, the bar's one list, and a panel that is the library or the selected item's options. From
 * 1100 dp the list and the panel stand side by side; below, the list is the panel's first part. [turn] counts the
 * times the bar's own menu asked for an item's options: each turns the panel to them.
 */
@Composable
internal fun BarPage(selected: String?, onSelect: (String?) -> Unit, turn: Int = 0) {
    val cfg by Store.config.collectAsState()
    val states by Ticker.states.collectAsState()
    val layout = LocalWindowLayout.current
    val wide = layout.kind == WindowClass.WIDE
    val res = LocalResources.current
    val view = LocalView.current
    val sideState = rememberSaveable { mutableStateOf(if (selected != null) PanelSide.OPTIONS else PanelSide.BAR) }
    val filter = rememberSaveable { mutableStateOf("") }
    val edit = remember { BarEdit(res, sideState, filter) }
    val reorder = remember { Reorder() }
    val item = cfg.items.firstOrNull { it.id == selected && it.section != Section.OFF }
    val side = when {
        edit.side == PanelSide.OPTIONS && item == null -> if (wide) PanelSide.ADD else PanelSide.BAR
        edit.side == PanelSide.BAR && wide -> PanelSide.ADD
        else -> edit.side
    }
    SideEffect {
        edit.onSelect = onSelect
        edit.wide = wide
        edit.selected = item?.id
        edit.showing = side
        reorder.onRefused = edit::refused
    }
    LaunchedEffect(turn) { if (turn > 0 && selected != null) edit.side = PanelSide.OPTIONS }
    // A reason is read, then the line is the preview's hint again.
    LaunchedEffect(edit.said) {
        if (edit.said == null) return@LaunchedEffect
        delay(4000)
        edit.said = null
    }
    DisposableEffect(edit, reorder, view) {
        BarKeys.handler = { native -> edit.key(KeyEvent(native), view.onCheckIsTextEditor(), reorder) }
        onDispose { BarKeys.handler = null }
    }
    Column(Modifier.fillMaxSize().onKeyEvent { edit.key(it, view.onCheckIsTextEditor(), reorder) }
        .padding(start = layout.marginDp.dp, end = layout.marginDp.dp, top = Space.m, bottom = Space.xl)) {
        BarPreview(states, item?.id, edit.said) { id -> if (id != null) edit.select(id) }
        // What a change came to, for a screen reader; the page draws nothing for it.
        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = edit.announced })
        Spacer(Modifier.height(Space.m))
        if (wide) Row(Modifier.weight(1f)) {
            BarList(cfg, states, item?.id, edit, reorder, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(Space.xl))
            Column(Modifier.width(layout.panelDp.dp)) {
                PanelSwitch(side, item, wide, edit)
                Spacer(Modifier.height(10.dp))
                Panel(side, item, cfg, states, edit, reorder, Modifier.weight(1f))
            }
        } else {
            PanelSwitch(side, item, wide, edit)
            Spacer(Modifier.height(10.dp))
            Panel(side, item, cfg, states, edit, reorder, Modifier.weight(1f))
        }
    }
}

/**
 * The panel's switch: "Add items", and the selected item by glyph and name ("Select an item", dimmed, with none);
 * below the width with room for both, "Your bar" first.
 */
@Composable
private fun PanelSwitch(side: PanelSide, item: ItemConfig?, wide: Boolean, edit: BarEdit) {
    val res = LocalResources.current
    val sides = if (wide) listOf(PanelSide.ADD, PanelSide.OPTIONS) else PanelSide.entries
    val parts = sides.map {
        when (it) {
            PanelSide.BAR -> SegmentPart(stringResource(R.string.page_bar))
            PanelSide.ADD -> SegmentPart(stringResource(R.string.page_add), icon = Sym.ADD)
            PanelSide.OPTIONS ->
                if (item != null) SegmentPart(itemName(res, item), icon = Items.of(item.type)?.icon ?: Sym.UPDATE)
                else SegmentPart(stringResource(R.string.panel_select_item), enabled = false)
        }
    }
    Segment(parts, sides.indexOf(side), {
        when (sides[it]) {
            PanelSide.BAR -> edit.side = PanelSide.BAR
            PanelSide.ADD -> edit.toLibrary()
            PanelSide.OPTIONS -> edit.side = PanelSide.OPTIONS
        }
    }, (if (LocalWindowLayout.current.kind == WindowClass.MID) Modifier.width(SWITCH_MID) else Modifier.fillMaxWidth()).testTag("panel-switch"), role = Role.Tab)
}

/** The panel's card. The card stays; only what is in it travels, a short way, when the side changes. */
@Composable
private fun Panel(side: PanelSide, item: ItemConfig?, cfg: BarConfig, states: Map<String, ItemState>, edit: BarEdit, reorder: Reorder, modifier: Modifier) {
    val shift = with(LocalDensity.current) { Space.l.roundToPx() }
    // The options on their way out are still the item's that was let go.
    val last = remember { object { var item: ItemConfig? = null } }
    if (item != null) last.item = item
    Box(modifier.fillMaxWidth().clip(CARD).background(Bento.colors.panel)) {
        AnimatedContent(side, transitionSpec = {
            val way = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(Motion.spec(tween(TURN_MS))) { way * shift } + fadeIn(Motion.spec(tween(TURN_MS)))) togetherWith
                (slideOutHorizontally(Motion.spec(tween(TURN_MS))) { -way * shift } + fadeOut(Motion.spec(tween(TURN_MS))))
        }, label = "panel") { shown ->
            when (shown) {
                PanelSide.BAR -> BarList(cfg, states, item?.id, edit, reorder, Modifier.fillMaxSize())
                PanelSide.ADD -> Library(cfg, edit)
                PanelSide.OPTIONS -> (item ?: last.item)?.let { of -> key(of.id) { ItemOptions(of, cfg, states[of.id], edit) } }
            }
        }
    }
}

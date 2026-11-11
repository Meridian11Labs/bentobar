package com.meridian11.books.bentobar.ui

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Display
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Part
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.data.homeArea
import com.meridian11.books.bentobar.data.partOf
import com.meridian11.books.bentobar.items.ItemGroup
import com.meridian11.books.bentobar.items.ItemType
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.items.WorldClock
import com.meridian11.books.bentobar.util.Sym
import com.meridian11.books.bentobar.util.SymIcon
import kotlinx.coroutines.launch

/** One of the words a remembered setup's line is made of. What each reads as is [setupText]'s business. */
sealed interface SetupWord {
    /** The item's own: its city, its text or its label, as the user gave it. */
    data class Own(val text: String) : SetupWord
    /** Weather where the device is. */
    data object Here : SetupWord
    data object TextOnly : SetupWord
    data object IconOnly : SetupWord
    /** A type that comes waiting on its rule, put in the bar for good. */
    data object Always : SetupWord
    /** It waits on its rule, which a fresh one doesn't, or on another number: the rule's short words. */
    data object Rule : SetupWord
    /** It differs only in options that have no words here. */
    data object Changed : SetupWord
}

/**
 * What a remembered setup's line says: the item's own words (its city, text or label; the first there is, and then
 * nothing else), failing that what differs from the [fresh] item the + makes of its [type], in the words the options
 * use. Never empty: a line always says something.
 */
fun setupWords(item: ItemConfig, type: ItemType, fresh: ItemConfig = type.fresh(item.id, item.area)): List<SetupWord> {
    fun own(key: String) = item.options[key]?.trim()?.takeIf { it.isNotEmpty() && it != fresh.options[key]?.trim() }
    if (item.options["where"] == "here" && fresh.options["where"] != "here") return listOf(SetupWord.Here)
    val own = own("city") ?: own("zone")?.let(WorldClock::cityOf) ?: own("text") ?: own("label")
    if (own != null) return listOf(SetupWord.Own(own))
    val number = type.trigger?.threshold
    return buildList {
        if (item.display != fresh.display) when (item.display) {
            Display.TEXT -> add(SetupWord.TextOnly)
            Display.ICON -> add(SetupWord.IconOnly)
            Display.ICON_AND_TEXT -> Unit
        }
        if (fresh.whenActive && !item.whenActive) add(SetupWord.Always)
        else if (item.whenActive && (!fresh.whenActive || (number != null && number.of(item) != number.of(fresh)))) add(SetupWord.Rule)
    }.ifEmpty { listOf(SetupWord.Changed) }
}

/** [setupWords] as the line reads: "London", "text only", "icon only · shows above 50%". */
fun setupText(res: Resources, item: ItemConfig, type: ItemType): String = setupWords(item, type).joinToString(" · ") {
    when (it) {
        is SetupWord.Own -> it.text
        SetupWord.Here -> res.getString(R.string.weather_here)
        SetupWord.TextOnly -> res.getString(R.string.row_text_only)
        SetupWord.IconOnly -> res.getString(R.string.row_icon_only)
        SetupWord.Always -> res.getString(R.string.library_always)
        SetupWord.Rule -> type.trigger?.short(item) ?: res.getString(R.string.row_when_active)
        SetupWord.Changed -> res.getString(R.string.library_as_set_up)
    }
}

/** A remembered setup under its type: the item as it waits in Off and its words; [forgotten] while Undo can still bring it back. */
data class Line(val item: ItemConfig, val words: String, val forgotten: Boolean = false)

/** A type in the library, with the setups remembered of it. */
data class Shelf(val type: ItemType, val name: String, val blurb: String, val inBar: Boolean, val lines: List<Line>)

/**
 * The library's list for [cfg]: every type, group by group, each with its remembered setups (the Off items of its type,
 * in the layout's order). An Off item of a type this version doesn't know has no row to stand under and is left alone.
 * [forgotten] is the id of a line to mark so.
 */
fun shelvesOf(cfg: BarConfig, name: (ItemType) -> String, blurb: (ItemType) -> String, forgotten: String? = null,
              words: (ItemConfig, ItemType) -> String): List<Shelf> =
    ItemGroup.entries.flatMap { Items.inGroup(it) }.map { type ->
        val mine = cfg.items.filter { it.type == type.type }
        Shelf(type, name(type), blurb(type), inBar = mine.any { it.section != Section.OFF },
            lines = mine.filter { it.section == Section.OFF }.map { Line(it, words(it, type), forgotten = it.id == forgotten) })
    }

/**
 * What the filter leaves: a type whose name or description has [query] in it, with all its lines; any other type only
 * with the lines whose words have it. No query leaves everything.
 */
fun List<Shelf>.matching(query: String): List<Shelf> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return mapNotNull { shelf ->
        if (shelf.name.contains(q, ignoreCase = true) || shelf.blurb.contains(q, ignoreCase = true)) shelf
        else shelf.lines.filter { it.words.contains(q, ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { shelf.copy(lines = it) }
    }
}

/** How many rows these are, for the filter's count: each type and each line. */
fun List<Shelf>.rows(): Int = sumOf { 1 + it.lines.size }

/** Something in the library to bring into view, and to give the keyboard focus if what was pressed had it. */
internal data class Seek(val key: String, val focus: Boolean, val serial: Int) {
    companion object {
        fun type(type: String) = "type:$type"
        fun line(id: String) = "line:$id"
        const val UNDO = "undo"
    }
}

/**
 * The row and the button a [Seek] for [key] goes to. Asked once, after the list has settled, then forgotten; the scroll
 * it started goes on in the row's own scope, so forgetting doesn't stop it halfway. A row [height] high is shown with
 * what stands [above] it (a line with its type's row) and a little of what follows, not at the card's very edge.
 */
@Composable
private fun sought(key: String, edit: BarEdit, height: Dp = 0.dp, above: Dp = 0.dp): Pair<BringIntoViewRequester, FocusRequester> {
    val bring = remember { BringIntoViewRequester() }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val seek = edit.seek
    val reach = if (height == 0.dp) null else with(LocalDensity.current) { Rect(0f, -above.toPx(), 1f, (height + Space.l).toPx()) }
    LaunchedEffect(seek) {
        if (seek?.key != key) return@LaunchedEffect
        withFrameNanos { }
        if (seek.focus) runCatching { focus.requestFocus() }
        scope.launch { runCatching { bring.bringIntoView(reach) } }
        if (edit.seek == seek) edit.seek = null
    }
    return bring to focus
}

/**
 * The library, the panel's "Add items": the filter, and every type in its group with a +; under a type the setups
 * remembered of it, each a line with Forget and a + that brings it back. What a + did is said on the type's row, with
 * Undo. With several [lanes] on screen "Add to" stands under the filter, set to the area in use or the one just opened,
 * and every + says where it adds. Every change is one of [edit]'s commands. A type's row and a setup's line can be
 * carried into the bar ([reorder]: they register where they stand; the one carried is dimmed meanwhile). While a row
 * of the bar is carried the filter's place is the well that says a drop here removes it; nothing else moves.
 */
@Composable
internal fun Library(cfg: BarConfig, edit: BarEdit, lanes: Lanes, reorder: Reorder, modifier: Modifier = Modifier) {
    val c = Bento.colors
    val res = LocalResources.current
    val kept by Undo.last.collectAsState()
    val added = Undo.added(kept, cfg)
    val forgotten = Undo.forgotten(kept, cfg)
    // A forgotten line keeps its place until the next change: the list is the one from before it was forgotten.
    val listed = kept?.before?.takeIf { forgotten != null } ?: cfg
    val shelves = remember(listed, forgotten?.id, res) {
        shelvesOf(listed, { res.getString(it.titleRes) }, { res.getString(it.blurbRes) }, forgotten?.id) { item, type -> setupText(res, item, type) }
    }
    val query = edit.filter
    val shown = remember(shelves, query) { shelves.matching(query) }
    val scroll = rememberScrollState()
    val field = remember { FocusRequester() }
    LaunchedEffect(edit.focusFilter) {
        if (!edit.focusFilter) return@LaunchedEffect
        withFrameNanos { }
        runCatching { field.requestFocus() }
        edit.focusFilter = false
    }
    LaunchedEffect(query) { scroll.scrollTo(0) }
    // Where a + adds, said only while there is a choice.
    val to = if (lanes.several) edit.addTo ?: cfg.homeArea() else null
    val toName = to?.let { stringResource(areaRes(it)) }
    // Something to go to that the filter hides, or that has no row here: nothing goes there later, out of the blue.
    LaunchedEffect(edit.seek) {
        val seek = edit.seek ?: return@LaunchedEffect
        repeat(3) { withFrameNanos { } }
        if (edit.seek == seek) edit.seek = null
    }
    Column(modifier.fillMaxSize().padding(start = Space.l, top = 14.dp, end = 6.dp).testTag("library")) {
        Box(Modifier.padding(end = 10.dp)) {
            FilterField(edit.filter, { edit.filter = it }, stringResource(R.string.library_filter), Modifier.testTag("filter"), field)
            DropWell(reorder, Modifier.matchParentSize())
        }
        if (query.isNotBlank()) Text(stringResource(R.string.add_filter_count, shown.rows(), shelves.rows()),
            Modifier.padding(start = Space.xs, top = 10.dp, end = Space.xs).semantics { liveRegion = LiveRegionMode.Polite }.testTag("filter-count"),
            style = Bento.type.help, color = c.ink3)
        if (to != null) Row(Modifier.fillMaxWidth().padding(top = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = stringResource(R.string.areas_add_to)
            val areas = areaChoices()
            Text(label, Modifier.padding(start = Space.xs), style = Bento.type.small, color = c.ink2, maxLines = 1)
            Spacer(Modifier.width(Space.m))
            Segment(areas.map { SegmentPart(it.second, spoken = "$label ${it.second}") }, areas.indexOfFirst { it.first == to }, { edit.addTo = areas[it].first },
                Modifier.weight(1f).testTag("add-to"))
        }
        Column(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { reorder.libraryView = it }.verticalScroll(scroll).padding(end = 10.dp, bottom = Space.m)) {
            ItemGroup.entries.forEach { group ->
                val here = shown.filter { it.type.group == group }
                if (here.isEmpty()) return@forEach
                key(group) { GroupHead(group) }
                here.forEach { shelf ->
                    key(shelf.type.type) { TypeRow(shelf, cfg, added?.takeIf { it.type == shelf.type.type }, edit, toName, reorder) }
                    shelf.lines.forEach { line ->
                        key(line.item.id) { if (line.forgotten) ForgotRow(shelf, line, edit) else LineRow(shelf, line, edit, toName, reorder) }
                    }
                }
            }
        }
    }
}

/** A row of the library while it is carried into the bar: still in its place, dimmed. */
private const val CARRIED_ALPHA = 0.4f

/**
 * The well: while a row of the bar is carried, what stands in the filter's place (and in the first row of an item's
 * options) says that a drop here removes it. It lies over what it replaces, in exactly its bounds, so nothing moves;
 * with the pointer over the panel it fills more.
 */
@Composable
internal fun DropWell(reorder: Reorder, modifier: Modifier = Modifier) {
    if (reorder.carried?.from != From.BAR || !reorder.live) return
    val c = Bento.colors
    val over = reorder.drop == Drop.Off
    val shape = RoundedCornerShape(Radius.medium)
    Box(modifier.testTag("well").background(c.panel, shape).background(c.accent.copy(alpha = if (over) 0.18f else 0.08f), shape)
        .border(if (over) 2.dp else 1.5.dp, c.accent, shape).padding(horizontal = Space.m), contentAlignment = Alignment.CenterStart) {
        Text(stringResource(R.string.library_drop_off), style = Bento.type.small.copy(fontWeight = FontWeight.SemiBold), color = c.accent, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupHead(group: ItemGroup) {
    val c = Bento.colors
    Column(Modifier.fillMaxWidth().padding(start = Space.xs, top = Space.m, bottom = Space.xs).testTag("group:${group.name}")) {
        Text(stringResource(group.title), Modifier.semantics { heading() }, style = Bento.type.help.copy(fontWeight = FontWeight.SemiBold), color = c.ink3)
        group.caption?.let { Text(stringResource(it), style = Bento.type.help, color = c.ink3) }
    }
}

/**
 * A type: glyph, name ("In your bar" after it), what it is, and for a type that comes waiting on its rule where its +
 * puts it. Right after a + it says "Added" in the description's place, with Undo. Its + always adds a fresh item.
 * [to] is the area's name while "Add to" is on screen: the + and both lines then say it.
 */
@Composable
private fun TypeRow(shelf: Shelf, cfg: BarConfig, added: ItemConfig?, edit: BarEdit, to: String?, reorder: Reorder) {
    val c = Bento.colors
    val type = shelf.type
    val (bring, plus) = sought(Seek.type(type.type), edit)
    val source = From.TYPE to type.type
    val carried = reorder.carried?.let { it.from == From.TYPE && it.type == type.type } == true
    Row(Modifier.bringIntoViewRequester(bring).testTag("type:${type.type}").fillMaxWidth().heightIn(min = TYPE_ROW)
        .onGloballyPositioned { reorder.sources[source] = it }.alpha(if (carried) CARRIED_ALPHA else 1f)
        .padding(start = Space.xs, top = Space.xs, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        SymIcon(type.icon, size = 18.sp, color = c.ink2)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shelf.name, Modifier.weight(1f, fill = false), style = Bento.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (shelf.inBar) Text(stringResource(R.string.library_in_bar), Modifier.padding(start = Space.s), style = Bento.type.help, color = c.ink3, maxLines = 1)
            }
            if (added != null) {
                val rule = if (added.whenActive && cfg.partOf(added) == Part.LOWER) type.trigger?.short(added) ?: stringResource(R.string.row_when_active) else null
                Row(Modifier.testTag("added"), verticalAlignment = Alignment.CenterVertically) {
                    SymIcon(Sym.CHECK, size = 14.sp, color = c.accent)
                    Spacer(Modifier.width(Space.xs))
                    val at = if (to != null) stringResource(areaRes(added.area)) else null
                    Text(when {
                        at != null && rule != null -> stringResource(R.string.add_added_to_rule, at, rule)
                        at != null -> stringResource(R.string.add_added_to, at)
                        rule != null -> stringResource(R.string.add_added_rule, rule)
                        else -> stringResource(R.string.add_added)
                    },
                        style = Bento.type.help.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(shelf.blurb, style = Bento.type.help, color = c.ink3)
                if (type.addsWhenActive) {
                    val rule = remember(type) { type.trigger?.short(type.fresh("", Position.RIGHT)) } ?: stringResource(R.string.row_when_active)
                    val chevron = cfg.hiddenMode != HiddenMode.SHOW_ALL
                    val words = if (to != null) stringResource(R.string.library_adds_to, to, rule)
                        else stringResource(if (chevron) R.string.library_adds_behind else R.string.library_adds_when_active, rule)
                    val spoken = words.replace("‹", stringResource(R.string.chevron_spoken))
                    Text(words, Modifier.semantics { if (chevron && to == null) contentDescription = spoken }.testTag("adds"),
                        style = Bento.type.help.copy(fontWeight = FontWeight.Medium), color = c.ink2)
                }
            }
        }
        Spacer(Modifier.width(Space.m).onGloballyPositioned { reorder.stops[source] = it })
        if (added != null) {
            LibraryUndo(edit)
            Spacer(Modifier.width(2.dp))
        }
        BentoIconButton(Sym.ADD, if (to != null) stringResource(R.string.library_add_to, shelf.name, to) else stringResource(R.string.library_add, shelf.name),
            { edit.add(type) }, Modifier.focusRequester(plus).testTag("add:${type.type}"))
    }
}

/** A remembered setup: its words, Forget, and the + that brings it back as it was. No glyph: it stands under its type's. */
@Composable
private fun LineRow(shelf: Shelf, line: Line, edit: BarEdit, to: String?, reorder: Reorder) {
    val c = Bento.colors
    val id = line.item.id
    val (bring, plus) = sought(Seek.line(id), edit, height = Target.row, above = TYPE_ROW)
    var onForget by remember { mutableStateOf(false) }
    var onPlus by remember { mutableStateOf(false) }
    val spoken = stringResource(R.string.library_setup_spoken, shelf.name, line.words)
    val add = if (to != null) stringResource(R.string.library_add_setup_to, shelf.name, line.words, to) else stringResource(R.string.library_add_setup, shelf.name, line.words)
    val forget = stringResource(R.string.library_forget_setup, shelf.name, line.words)
    val addAction = stringResource(R.string.add_add)
    val forgetAction = stringResource(R.string.library_forget)
    val actions = remember(id, addAction, forgetAction) {
        listOf(CustomAccessibilityAction(addAction) { edit.addSetup(id, focus = false); true },
            CustomAccessibilityAction(forgetAction) { edit.forget(id, focus = false); true })
    }
    val source = From.LINE to id
    val carried = reorder.carried?.let { it.from == From.LINE && it.id == id } == true
    Row(Modifier.bringIntoViewRequester(bring).testTag("line:$id").fillMaxWidth().height(Target.row)
        .onGloballyPositioned { reorder.sources[source] = it }.alpha(if (carried) CARRIED_ALPHA else 1f).padding(start = LINE_INDENT),
        verticalAlignment = Alignment.CenterVertically) {
        Text(line.words, Modifier.weight(1f).semantics { contentDescription = spoken; customActions = actions },
            style = Bento.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BentoButton(stringResource(R.string.library_forget), { edit.forget(id, onForget) },
            Modifier.onGloballyPositioned { reorder.stops[source] = it }.onFocusChanged { onForget = it.isFocused }.semantics { contentDescription = forget }.testTag("forget:$id"), kind = ButtonKind.QUIET)
        Spacer(Modifier.width(Space.xs))
        BentoIconButton(Sym.ADD, add, { edit.addSetup(id, onPlus) }, Modifier.focusRequester(plus).onFocusChanged { onPlus = it.isFocused }.testTag("add-line:$id"))
    }
}

/** A forgotten line's place, until the next change: what was forgotten, and the Undo that brings it back. */
@Composable
private fun ForgotRow(shelf: Shelf, line: Line, edit: BarEdit) {
    Row(Modifier.testTag("forgot").fillMaxWidth().height(Target.row).padding(start = LINE_INDENT), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.library_forgot, stringResource(R.string.library_setup, shelf.name, line.words)), Modifier.weight(1f),
            style = Bento.type.small, color = Bento.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LibraryUndo(edit)
    }
}

/** The one Undo, where the library says what it just did. */
@Composable
private fun LibraryUndo(edit: BarEdit) {
    val (bring, focus) = sought(Seek.UNDO, edit)
    var focused by remember { mutableStateOf(false) }
    BentoButton(stringResource(R.string.bar_undo), { edit.undo(focused) },
        Modifier.bringIntoViewRequester(bring).focusRequester(focus).onFocusChanged { focused = it.isFocused }.testTag("library-undo"), kind = ButtonKind.TEXT)
}

/** A line starts where its type's name does: the glyph's room and the gap after it. */
private val LINE_INDENT = 34.dp
/** A type's row at its lowest, whether its description takes one line or two. */
private val TYPE_ROW = 64.dp

package com.meridian11.books.bentobar.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.meridian11.books.bentobar.bar.SliderMath
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.util.Sym
import com.meridian11.books.bentobar.util.SymIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/** A labelled group of single-choice chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(label: String, choices: List<Pair<T, String>>, selected: T, help: String? = null, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            choices.forEach { (value, text) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) },
                    leadingIcon = if (value == selected) { { SymIcon(com.meridian11.books.bentobar.util.Sym.CHECK) } } else null)
            }
        }
    }
}

/** The bar's three areas as choices, in the order they stand in the status bar. */
@Composable
fun areaChoices(): List<Pair<Position, String>> = listOf(Position.LEFT to stringResource(R.string.areas_left),
    Position.CENTER to stringResource(R.string.areas_middle), Position.RIGHT to stringResource(R.string.areas_right))

@Composable
fun SwitchRow(label: String, checked: Boolean, help: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // The whole row is the switch: clicking the label toggles it, and a screen reader hears its name.
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A text field that saves as you type. While it has the focus it keeps what is typed: a caller may
 * store something else (nothing for a blank, a shortened text) and the field must not change under
 * the cursor. It shows the stored [value] again when the focus goes, and whenever the value changes
 * while it doesn't have it. [maxLength]: no more can be typed than the caller keeps.
 */
@Composable
fun TextRow(label: String, value: String, help: String? = null, placeholder: String = "", numeric: Boolean = false,
            /** Shown instead of [help], with the field marked, while the text can't be used. */
            error: String? = null, maxLength: Int = Int.MAX_VALUE, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = value }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { typed -> typed.take(maxLength).let { text = it; onChange(it) } },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        (error ?: help)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}

@Composable
fun SliderRow(label: String, value: Int, range: IntRange, format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    var v by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { v = value.toFloat() }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(format(SliderMath.stop(v)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onChange(SliderMath.stop(v)) },
            valueRange = range.first.toFloat()..range.last.toFloat(), steps = SliderMath.stops(range),
            // Read out as its value and unit ("12 dp"), not as a percentage of the track.
            modifier = Modifier.semantics { contentDescription = label; stateDescription = format(SliderMath.stop(v)) })
    }
}

/**
 * The words of a [ServiceKey], as text resources: each service's own ("AirLabs key", "Remove Finnhub
 * key"). [consent]: what is sent and to whom, before a key is saved; [help]: under the field.
 */
class KeyWords(
    @StringRes val label: Int, @StringRes val consent: Int, @StringRes val getKey: Int, @StringRes val save: Int, @StringRes val help: Int,
    @StringRes val saved: Int, @StringRes val replace: Int, @StringRes val remove: Int, @StringRes val removeHelp: Int,
)

/**
 * A service's key in an item's settings (Flight's, Stocks'), where a long paste is easier than in a
 * drop-down. Without one: what is sent and to whom, where a key is got ([onGetKey], in the browser),
 * and a field that shows dots. With one ([keyed]): that it is saved, what [more] adds (Flight: the
 * lookups left), and the two ways to change that. A saved key is never shown again, here or anywhere.
 * [onSave] says whether the key was taken. [heading]: where the key is one part of the item's
 * settings (Stocks'), its name over it.
 */
@Composable
fun ServiceKey(keyed: Boolean, words: KeyWords, onSave: (String) -> Boolean, onRemove: () -> Unit, onGetKey: () -> Unit,
               heading: String? = null, more: @Composable () -> Unit = {}) {
    var replacing by remember { mutableStateOf(false) }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (heading != null) Text(heading, style = MaterialTheme.typography.labelLarge)
        if (keyed && !replacing) {
            Text(stringResource(words.saved), style = MaterialTheme.typography.bodyLarge)
            more()
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { replacing = true }) { Text(stringResource(words.replace)) }
                // At once, with no dialog and no Undo: an Undo would mean keeping the key after it was removed.
                TextButton(onClick = onRemove) { Text(stringResource(words.remove), color = MaterialTheme.colorScheme.error) }
            }
            Text(stringResource(words.removeHelp), style = MaterialTheme.typography.bodySmall, color = quiet)
        } else {
            if (!keyed) {
                Text(stringResource(words.consent), style = MaterialTheme.typography.bodyMedium)
                val getKey = stringResource(words.getKey)
                val opens = stringResource(R.string.common_opens_browser, getKey)
                TextButton(onClick = onGetKey, modifier = Modifier.semantics { contentDescription = opens }) { Text(getKey) }
            }
            KeyField(stringResource(words.label), stringResource(words.save), onSave = { if (onSave(it)) replacing = false },
                onCancel = if (replacing) { { replacing = false } } else null)
            Text(stringResource(words.help), style = MaterialTheme.typography.bodySmall, color = quiet, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}

/**
 * Where the key is pasted: [label] names it, [save] is the button. A password field: it shows dots,
 * and no keyboard learns it. What is typed is held only while it is typed (never with the window's
 * saved state), and is gone from here the moment it is saved.
 */
@Composable
private fun KeyField(label: String, save: String, onSave: (String) -> Unit, onCancel: (() -> Unit)?) {
    var typed by remember { mutableStateOf("") }
    fun saveIt() {
        if (typed.isBlank()) return
        onSave(typed)
        typed = ""
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.take(200) },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { saveIt() }),
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                // A hardware keyboard's Enter saves too. Down acts and up is swallowed, so the keyboard's own action can't act a second time.
                if (e.key == Key.Enter || e.key == Key.NumPadEnter) { if (e.type == KeyEventType.KeyDown) saveIt(); true } else false
            },
        )
        FilledTonalButton(onClick = { saveIt() }, enabled = typed.isNotBlank()) { Text(save, maxLines = 1) }
        if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), maxLines = 1) }
    }
}

// The settings window's own controls, on its tokens (Theme.kt). The rows above still serve the bar's popups and the
// pages that have not been redesigned yet, in Material's shapes.

/**
 * What a control that takes a click shows of itself: a faint veil under the pointer and a press, and the focus ring
 * (2 dp of accent, 2 dp off the control) while it has the keyboard. [inset] takes that much off the top and bottom of
 * what is drawn, for a control whose target is taller than it looks (a tab). Nothing opens or changes on hover.
 */
@Composable
fun Modifier.states(interaction: InteractionSource, radius: Dp = Radius.medium, inset: Dp = 0.dp, ground: Boolean = true,
                    veil: Color = Bento.colors.ink): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val ring = Bento.colors.accent
    return drawWithContent {
        val top = inset.toPx()
        val r = radius.toPx()
        drawContent()
        val depth = if (!ground) 0f else if (pressed) 0.10f else if (hovered) 0.06f else 0f
        if (depth > 0f) drawRoundRect(veil.copy(alpha = depth), Offset(0f, top), Size(size.width, size.height - 2 * top), CornerRadius(r))
        if (focused) {
            val w = 2.dp.toPx()
            val out = 2.dp.toPx() + w / 2
            drawRoundRect(ring, Offset(-out, top - out), Size(size.width + 2 * out, size.height - 2 * top + 2 * out), CornerRadius(r + out), style = Stroke(w))
        }
    }
}

/** A switch as it looks: the track, and the thumb that travels. What is around it takes the click (`toggleable`), as with Material's. */
@Composable
fun SwitchMark(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Bento.colors
    val at by animateFloatAsState(if (checked) 1f else 0f, Motion.spec(tween(SWITCH_MS)), label = "switch")
    Canvas(modifier.size(40.dp, 24.dp).alpha(if (enabled) 1f else DIMMED)) {
        drawRoundRect(lerp(c.edge, c.accent, at), cornerRadius = CornerRadius(size.height / 2))
        drawCircle(lerp(c.panel, c.onPrimary, at), 9.dp.toPx(), Offset(12.dp.toPx() + at * 16.dp.toPx(), size.height / 2))
    }
}

/**
 * Filled: the one such button on a screen. Tonal: every other command. Text: a quiet one in a line (Undo). Plain: a command
 * as plain words, where nothing is lost by it (Remove from the bar). Quiet: one that stands back beside the line's own
 * button (Forget). Danger: one that can't be taken back.
 */
/** [WARN] is the one button of an amber notice, in the notice's own ink. */
enum class ButtonKind { FILLED, TONAL, TEXT, PLAIN, QUIET, DANGER, WARN }

@Composable
fun BentoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: ButtonKind = ButtonKind.TONAL, icon: String? = null, enabled: Boolean = true) {
    val c = Bento.colors
    val interaction = remember { MutableInteractionSource() }
    val ground = when (kind) {
        ButtonKind.FILLED -> c.primary
        ButtonKind.TONAL -> c.sunk
        ButtonKind.WARN -> c.warnInk.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val ink = when (kind) {
        ButtonKind.FILLED -> c.onPrimary
        ButtonKind.TONAL, ButtonKind.PLAIN -> c.ink
        ButtonKind.TEXT -> c.accent
        ButtonKind.QUIET -> c.ink2
        ButtonKind.DANGER -> c.danger
        ButtonKind.WARN -> c.warnInk
    }
    val pad = when (kind) { ButtonKind.FILLED -> 18.dp; ButtonKind.TONAL, ButtonKind.WARN -> Space.l; else -> Space.m }
    Row(modifier.height(Target.control).alpha(if (enabled) 1f else DIMMED)
        .states(interaction, veil = ink)
        .background(ground, RoundedCornerShape(Radius.medium))
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = pad), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            SymIcon(icon, size = 18.sp, color = ink)
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = if (kind == ButtonKind.PLAIN) Bento.type.label else Bento.type.strong, color = ink, maxLines = 1)
    }
}

/** A square tonal button with one glyph (Order's arrows); [description] is its spoken name. */
@Composable
fun BentoIconButton(icon: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Bento.colors
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.size(Target.control).alpha(if (enabled) 1f else DIMMED)
        .states(interaction)
        .background(c.sunk, RoundedCornerShape(Radius.medium))
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        SymIcon(icon, size = 18.sp, color = c.ink, contentDescription = description)
    }
}

/** A quiet button that opens a short menu of [choices] and shows the [chosen] one (an item's Area, with one area in use). */
@Composable
fun <T> MenuButton(choices: List<Pair<T, String>>, chosen: T, onChoose: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Bento.colors
    val interaction = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.width(132.dp).height(Target.control)
            .states(interaction)
            .background(c.sunk, RoundedCornerShape(Radius.medium))
            .clickable(interaction, indication = null, role = Role.DropdownList) { open = true }
            .padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(choices.firstOrNull { it.first == chosen }?.second.orEmpty(), Modifier.weight(1f), style = Bento.type.strong, color = c.ink, maxLines = 1)
            SymIcon(Sym.EXPAND_MORE, size = 18.sp, color = c.ink2)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.raised) {
            choices.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label, style = if (value == chosen) Bento.type.strong else Bento.type.body, color = c.ink) },
                    onClick = { open = false; if (value != chosen) onChoose(value) })
            }
        }
    }
}

/** One part of a [Segment]. A part that can't be chosen right now is dimmed; its reason is said in words beside the segment. */
/** [spoken] is read out in the label's place, for a label with a sign in it (‹). */
class SegmentPart(val label: String, val icon: String? = null, val enabled: Boolean = true, val spoken: String? = null)

/**
 * A segmented choice: parts on a sunk track, the chosen one raised and heavier (never by color alone). The raised part
 * travels to the chosen one. [role] is a tab for the panel switch and a radio button for a choice of values; [chosen]
 * outside the parts raises none. The parts are [equal] in width, or each as wide as its words with the room left over
 * shared out evenly, so a long choice beside a short one is not cut short.
 */
@Composable
fun Segment(parts: List<SegmentPart>, chosen: Int, onChoose: (Int) -> Unit, modifier: Modifier = Modifier,
            role: Role = Role.RadioButton, track: Color = Bento.colors.sunk, equal: Boolean = true) {
    val c = Bento.colors
    val type = Bento.type
    val at by animateFloatAsState(chosen.toFloat(), Motion.spec(tween(SEGMENT_MS)), label = "segment")
    // Where each part was placed and how wide, for the raised part that is drawn behind them.
    var spans by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
    Layout({
        parts.forEachIndexed { i, part ->
            val interaction = remember { MutableInteractionSource() }
            val up = i == chosen
            val ink = if (part.enabled) c.ink else c.ink3
            Row(Modifier.height(34.dp)
                .states(interaction, radius = Radius.small, ground = !up)
                .selectable(up, interaction, indication = null, enabled = part.enabled, role = role) { onChoose(i) }
                .then(if (part.spoken != null) Modifier.clearAndSetSemantics { contentDescription = part.spoken } else Modifier)
                .padding(horizontal = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (part.icon != null) {
                    SymIcon(part.icon, size = 15.sp, color = ink)
                    Spacer(Modifier.width(6.dp))
                }
                Text(part.label, style = type.small.copy(fontWeight = if (!part.enabled) FontWeight.Normal else if (up) FontWeight.SemiBold else FontWeight.Medium),
                    color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }, modifier.height(Target.control).background(track, RoundedCornerShape(Radius.medium)).padding(3.dp).selectableGroup().drawBehind {
        if (chosen !in parts.indices || spans.size != parts.size) return@drawBehind
        val from = spans[at.toInt().coerceIn(parts.indices)]
        val to = spans[kotlin.math.ceil(at).toInt().coerceIn(parts.indices)]
        val f = at - at.toInt()
        val x = from.first + (to.first - from.first) * f
        val w = from.second + (to.second - from.second) * f
        val r = CornerRadius(Radius.small.toPx())
        // The contact shadow that lifts it off the track.
        drawRoundRect(Color.Black.copy(alpha = if (c.dark) 0.40f else 0.14f), Offset(x, 1.dp.toPx()), Size(w, size.height), r)
        drawRoundRect(c.raised, Offset(x, 0f), Size(w, size.height), r)
    }) { measurables, cs ->
        val n = measurables.size
        val gap = SEGMENT_GAP.roundToPx()
        val wants = measurables.map { it.maxIntrinsicWidth(cs.maxHeight) }
        val room = if (cs.hasBoundedWidth) (cs.maxWidth - gap * (n - 1)).coerceAtLeast(0) else wants.sum()
        val widths = when {
            n == 0 -> emptyList()
            equal -> List(n) { room / n }
            wants.sum() <= room -> wants.map { it + (room - wants.sum()) / n }
            else -> wants.map { (it.toLong() * room / wants.sum()).toInt() }
        }.toMutableList()
        // What rounding left over goes to the last part, so the parts end where the track does.
        if (n > 0) widths[n - 1] += room - widths.sum()
        val placed = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i].coerceAtLeast(0))) }
        val height = if (cs.hasBoundedHeight) cs.maxHeight else placed.maxOfOrNull { it.height } ?: 0
        var x = 0
        val now = placed.map { p -> (x to p.width).also { x += p.width + gap } }
        if (now != spans) spans = now
        layout(room + gap * (n - 1).coerceAtLeast(0), height) {
            placed.forEachIndexed { i, p -> p.placeRelative(now[i].first, (height - p.height) / 2) }
        }
    }
}

/**
 * A filter's field: sunk, a search glyph, [label] as its placeholder and spoken name, and at its end the keys that go
 * with it: Ctrl F, which brings the keyboard here, and Esc, which empties it, once it has the keyboard or holds
 * something. Esc is the field's own while there is something to empty.
 */
@Composable
fun FilterField(text: String, onText: (String) -> Unit, label: String, modifier: Modifier = Modifier, focus: FocusRequester = remember { FocusRequester() }) {
    val c = Bento.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // The field's own value: text put in from outside (a letter typed elsewhere in the window) leaves the caret at its end.
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (value.text != text) value = TextFieldValue(text, TextRange(text.length))
    BasicTextField(value, { value = it; if (it.text != text) onText(it.text) },
        modifier.fillMaxWidth().height(Target.control)
            .states(interaction, ground = false)
            .background(c.sunk, RoundedCornerShape(Radius.medium))
            .focusRequester(focus)
            .onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.Escape && text.isNotEmpty() && run { onText(""); true } }
            .semantics { contentDescription = label },
        textStyle = Bento.type.body.copy(color = c.ink), singleLine = true, cursorBrush = SolidColor(c.accent), interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { inner ->
            Row(Modifier.padding(start = Space.m, end = Space.s), verticalAlignment = Alignment.CenterVertically) {
                SymIcon(Sym.SEARCH, size = 16.sp, color = c.ink3)
                Spacer(Modifier.width(Space.s))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(label, style = Bento.type.body, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                Spacer(Modifier.width(Space.s))
                if (focused || text.isNotEmpty()) Keycap(stringResource(R.string.key_esc))
                else {
                    Keycap(stringResource(R.string.key_ctrl))
                    Spacer(Modifier.width(3.dp))
                    Keycap(stringResource(R.string.key_f))
                }
            }
        })
}

/** A key's name as a cap, in a line of help or at a field's end (where the field's ground is the cap's own). */
@Composable
fun Keycap(text: String, modifier: Modifier = Modifier) {
    val c = Bento.colors
    Text(text, modifier.background(c.sunk, RoundedCornerShape(Radius.small)).padding(horizontal = 5.dp),
        style = Bento.type.help.copy(fontWeight = FontWeight.Medium), color = c.ink3, maxLines = 1)
}

private val SEGMENT_GAP = 2.dp
private const val SEGMENT_MS = 160
private const val SWITCH_MS = 120
private const val DIMMED = 0.45f

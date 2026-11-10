package com.meridian11.books.bentobar.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.BarRules
import com.meridian11.books.bentobar.bar.Contrast
import com.meridian11.books.bentobar.bar.STRIP_PILL_PADDING
import com.meridian11.books.bentobar.bar.Strip
import com.meridian11.books.bentobar.bar.StripContent
import com.meridian11.books.bentobar.bar.StripEntry
import com.meridian11.books.bentobar.bar.StripEvents
import com.meridian11.books.bentobar.bar.StripLook
import com.meridian11.books.bentobar.data.ColorMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.areasInUse
import com.meridian11.books.bentobar.data.homeArea
import com.meridian11.books.bentobar.data.spacingDp
import com.meridian11.books.bentobar.data.stripPlan
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.items.Ticker
import com.meridian11.books.bentobar.util.Dates
import com.meridian11.books.bentobar.util.Fonts
import com.meridian11.books.bentobar.util.Sym
import com.meridian11.books.bentobar.util.SymIcon
import java.time.ZonedDateTime

/**
 * A mock status bar with the real strips in it, one for each area the layout uses, standing where the bar has them;
 * clicking an item selects it. Under it one quiet line: what the preview is, or [said], what a key just could not do.
 * Internal for its test.
 */
@Composable
internal fun BarPreview(states: Map<String, ItemState>, selected: String?, said: String? = null, onSelect: (String?) -> Unit) {
    val cfg by Store.config.collectAsState()
    val tick by Ticker.tick.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    // The preview is narrower than a status bar: what it has no room for is said under it, or an item
    // that is in the list and not in the picture looks lost.
    // Each area's own, as each of the bar's strips has its own.
    val dropped = remember { mutableStateMapOf<Position, Set<String>>() }
    // The areas the bar has a strip for, in the order it gives them room: with every item in one area, that one.
    val areas = BarRules.wantedAreas(cfg.homeArea(), cfg.areasInUse())
    // An area that left the layout takes what it had no room for with it: back later, it starts as a new strip does.
    SideEffect { dropped.keys.retainAll(areas.toSet()) }
    // What the bar shows, by the same function: the preview makes no choice of its own.
    val plans = areas.associateWith { cfg.stripPlan(it, { item -> states[item.id]?.active == true }, expanded, !dropped[it].isNullOrEmpty()) }
    val more = areas.flatMapTo(HashSet()) { dropped[it].orEmpty() }
    val entries = { list: List<ItemConfig> -> list.map { StripEntry(it, states[it.id] ?: Ticker.placeholder(it)) } }
    // The preview uses the strip's real colours: the sampled bar when BentoBar is running and the
    // colour is automatic, else the forced Light or Dark choice.
    val live by com.meridian11.books.bentobar.bar.BarLook.current.collectAsState()
    val bar = when (cfg.color) {
        ColorMode.LIGHT -> Contrast.resolve(Color.White, null)
        ColorMode.DARK -> Contrast.resolve(Contrast.DARK_TEXT, null)
        ColorMode.AUTO -> live ?: Contrast.resolve(Color.White, null)
    }
    val look = StripLook(bar.fg, bar.barDark, cfg.textSize, cfg.spacingDp.dp, cfg.pill, bar.background)
    val barBrush = when {
        bar.opaque -> androidx.compose.ui.graphics.SolidColor(bar.background)
        bar.barDark -> Brush.horizontalGradient(listOf(Color(0xFF5B6F8A), Color(0xFF3F6F73), Color(0xFF4F7E78)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFDDE6F3), Color(0xFFD5E8E6), Color(0xFFE3EEE9)))
    }
    val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
    // One object for the page's life, as the bar's own events are: rebuilt each time, every item would redraw on every tick.
    val events = remember { object : StripEvents {
        override fun click(item: ItemConfig, at: Rect) = select(item.id)
        override fun context(item: ItemConfig, at: Rect) = select(item.id)
        // The picture only selects: a wheel over it must not start a timer that later rings, or change the volume.
        override fun scroll(item: ItemConfig, steps: Int) {}
        // A slider is only drawn here: a click on it selects the item, like a click anywhere on an item.
        override val slidable: Boolean get() = false
        // Nor can an item be picked up: Sections' rows are where the order changes.
        override val draggable: Boolean get() = false
        override fun chevron(at: Rect) { expanded = !expanded }
        override fun chevronContext(at: Rect) { expanded = !expanded }
        override fun hover(inside: Boolean) {}
        override fun placed(id: String, at: Rect) {}
    } }
    Column {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.large))
                .background(barBrush),
        ) {
            // The strip may take what the mock clock and system icons leave (about 380 dp go to them and the gaps).
            val maxPx = with(LocalDensity.current) { (maxWidth - 380.dp).coerceAtLeast(200.dp).roundToPx() }
            val now = remember(tick) { ZonedDateTime.now() }
            // The system's 12/24-hour setting and the locale's own short date, like the real status bar.
            val h24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Dates.format(Dates.timeSkeleton(h24), now) + "   " + Dates.format("EEEMMMd", now), color = look.fg,
                    fontFamily = Fonts.bar, fontWeight = Fonts.barWeight, fontSize = 14.sp)
                // One area's strip, as the bar composes each of its own: the area's plan, its ‹ on the area's side, and what
                // it has no room for kept as the area's. [budget] is what its items may take.
                // An area the layout has just lost still has its place in [PreviewAreas] until that is measured again, and
                // is composed once more before then, with this layout: there is no plan for it, and it composes nothing.
                val strip: @Composable (Position, Int) -> Unit = { area, budget ->
                    plans[area]?.let { plan ->
                        Strip(entries(plan.visible), entries(plan.revealed), plan.chevron,
                            chevronAlways = plan.chevronAlways, chevronReservePx = 0, expanded = plan.open,
                            chevronOnLeft = area != Position.LEFT, look = look, maxWidthPx = budget, heightDp = 52.dp, events = events,
                            onOverflow = { dropped[area] = it })
                    }
                }
                val only = areas.singleOrNull()
                if (only != null) {
                    // A layout in one area is the one strip between the spacers it always had.
                    if (only == Position.LEFT) Spacer(Modifier.width(16.dp)) else Spacer(Modifier.weight(1f))
                    strip(only, maxPx)
                    if (only == Position.LEFT) Spacer(Modifier.weight(1f)) else if (only == Position.CENTER) Spacer(Modifier.weight(1f))
                } else {
                    // The bar's rule for an empty strip, asked here as there: an area that draws nothing is not in the
                    // picture, and the empty capsule is the home area's while no other area draws.
                    fun draws(area: Position, home: Boolean) = plans.getValue(area).let {
                        StripContent.drawn(entries(it.visible) + entries(it.revealed), it.chevron, look.pill, home)
                    }
                    val drawing = areas.filterTo(HashSet()) { area ->
                        draws(area, home = area == cfg.homeArea() && areas.none { it != area && draws(it, home = false) })
                    }
                    PreviewAreas(areas, drawing, maxPx, Modifier.weight(1f), strip)
                }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("US", color = look.fg, fontFamily = Fonts.bar, fontWeight = Fonts.barWeight, fontSize = 13.sp)
                    SymIcon(Sym.NOTIFICATIONS, size = 18.sp, color = look.fg)
                    SymIcon(Sym.WIFI, size = 18.sp, color = look.fg)
                    SymIcon(Sym.BATTERY_FULL, size = 18.sp, color = look.fg)
                }
            }
        }
        val hint = stringResource(if (plans.values.any { it.hidden.isNotEmpty() }) R.string.bar_preview_hint_hidden else R.string.bar_preview_hint)
        Text(said ?: if (more.isEmpty()) hint else hint + " " + pluralStringResource(R.plurals.bar_preview_more, more.size, more.size),
            style = Bento.type.help, color = Bento.colors.ink2, maxLines = 2,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/**
 * The strips of a layout in several areas, in the room between the mock clock and the mock system icons: where the bar
 * has them, by the bar's own rule of room ([BarRules.lane], [BarRules.middleLeft]). Right has the room first, then Left,
 * then the Middle; Right stands at the right end, Left at the left end and the Middle in the middle, moved only as far as
 * a neighbour makes it. They are sides of the screen, so the picture is not mirrored around them in a right to left
 * language. [areas] are in that order of room; [drawing] are the ones that put anything on the screen; [strip] composes
 * an area's strip whose items may take the width it is given, never more than [maxPx].
 *
 * A strip's items get its room less the strip's two ends and the room of a ‹, whether it has a ‹ or not: the bar keeps
 * that room only once a ‹ is needed, and the picture is steadier for keeping it always. An area with no room for a ‹ is
 * not drawn (the bar hands its items to another strip's ‹; here they are in the count under the picture), and one that
 * draws nothing takes no room.
 */
@Composable
private fun PreviewAreas(areas: List<Position>, drawing: Set<Position>, maxPx: Int, modifier: Modifier, strip: @Composable (Position, Int) -> Unit) {
    // Composed while measuring: a strip's budget comes from what the areas before it measured.
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val rtl = layoutDirection == LayoutDirection.Rtl
        // 16 dp stay free beside the clock, as they always did before a strip on the left.
        val beside = 16.dp.roundToPx()
        val lo = if (rtl) 0 else beside
        val hi = if (rtl) width - beside else width
        val ends = STRIP_PILL_PADDING.roundToPx()
        val chevron = 30.dp.roundToPx()
        var usedRight = 0
        var usedLeft = 0
        val placed = areas.mapNotNull { area ->
            val lane = BarRules.lane(area, lo, hi, between = 6.dp.roundToPx(), usedRight, usedLeft)
            val room = lane.max >= chevron + 2 * ends
            // With no room it is still composed, on no budget: what it would show is what the picture has no room for.
            val budget = if (room) minOf(maxPx, lane.max - chevron - 2 * ends) else 0
            // An area whose strip composed nothing has nothing to place.
            val p = subcompose(area) { strip(area, budget) }.singleOrNull()?.measure(Constraints(maxWidth = maxOf(0, lane.max), maxHeight = constraints.maxHeight))
            if (p == null || !room || area !in drawing || p.width == 0) return@mapNotNull null
            val x = when (area) {
                Position.RIGHT -> hi - p.width
                Position.LEFT -> lo
                Position.CENTER -> {
                    // Centred where a layout in the middle is: the page's Row gives an odd pixel to the second of its two spacers.
                    val half = (width - p.width) / 2
                    val natural = if (rtl) width - p.width - half else half
                    // With no neighbour it has nothing to clear and stays there.
                    if (usedRight == 0 && usedLeft == 0) natural else BarRules.middleLeft(lane, p.width, natural)
                }
            }
            if (area == Position.RIGHT) usedRight = p.width else if (area == Position.LEFT) usedLeft = p.width
            p to x
        }
        val height = placed.maxOfOrNull { it.first.height } ?: 0
        layout(width, height) { placed.forEach { (p, x) -> p.place(x, (height - p.height) / 2) } }
    }
}

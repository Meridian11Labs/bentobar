package com.meridian11.books.bentobar.items

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.ProgressRules.Of
import com.meridian11.books.bentobar.ui.ChoiceRow
import com.meridian11.books.bentobar.ui.LocalMenuGlass
import com.meridian11.books.bentobar.ui.MenuCard
import com.meridian11.books.bentobar.ui.MenuDivider
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.SliderRow
import com.meridian11.books.bentobar.ui.SwitchRow
import com.meridian11.books.bentobar.ui.fadeIn
import com.meridian11.books.bentobar.ui.rememberTick
import com.meridian11.books.bentobar.util.Dates
import com.meridian11.books.bentobar.util.Fmt
import com.meridian11.books.bentobar.util.Now
import com.meridian11.books.bentobar.util.Sym
import com.meridian11.books.bentobar.util.SymIcon
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Day progress: how much of the day, workday, week, month, year or life has elapsed or remains,
 * rendered in the bar with Material 3 Expressive Wavy / Track / Ring / Pie graphics or the filling clock glyph,
 * and frosted glass expressive cards inside its popup menu.
 */
object ProgressItem : ItemType("progress", R.string.item_progress_title, Sym.CLOCK_LOADER_40, R.string.item_progress_desc) {
    override val group = ItemGroup.TIME
    override val refreshMs = 5_000L
    override val menuWidthDp = 344

    internal val of = Opt.oneOf("of", "day", "day", "workday", "week", "month", "year", "life")
    internal val mode = Opt.oneOf("mode", "elapsed", "elapsed", "remaining")
    internal val style = Opt.oneOf("style", "wavy", "wavy", "track", "ring", "pie", "glyph")
    internal val bars = Opt.oneOf("bars", "one", "one", "day_year", "all")
    internal val stack = Opt.oneOf("stack", "vertical", "vertical", "horizontal")
    internal val tint = Opt.oneOf("tint", "accent", "accent", "bar")
    internal val width = IntOpt("width", 56, 24..100)
    internal val from = IntOpt("from", 9, 0..23)
    internal val to = IntOpt("to", 17, 1..24)
    internal val showLife = Opt.bool("showLife", false)
    internal val yearMonth = IntOpt("yearM", 1, 1..12)
    internal val yearDay = IntOpt("yearD", 1, 1..31)
    internal val lifeStart = IntOpt("lifeStart", 1990, 1920..2030)
    internal val lifeEnd = IntOpt("lifeEnd", 2070, 2030..2130)

    /** The test hook's moment, shown instead of now; null: none staged. */
    @Volatile private var staged: ZonedDateTime? = null

    private fun now(): ZonedDateTime = staged ?: ZonedDateTime.ofInstant(Instant.ofEpochMilli(Now.wall()), ZoneId.systemDefault())
    private fun weekStart() = ProgressRules.weekStart(Locale.getDefault())

    private val words = ProgressRules.Words(
        minutes = { Env.str(R.string.common_bar_minutes, it) },
        hoursMinutes = { h, m -> Env.str(R.string.common_bar_hours_minutes, h, m) },
        days = { Env.plural(R.plurals.progress_days, it.toInt(), it) },
        years = { Env.plural(R.plurals.progress_years, it.toInt(), it) },
        left = { Env.str(R.string.progress_left, it) },
        elapsed = { Env.str(R.string.progress_gone, it) },
        over = { Env.str(R.string.progress_over) },
    )
    private val spoken = ProgressRules.Words(
        minutes = { Fmt.words.aloud(0, 0, it, 0) },
        hoursMinutes = { h, m -> Fmt.words.aloud(0, h, m, 0) },
        days = { Env.plural(R.plurals.progress_days, it.toInt(), it) },
        years = { Env.plural(R.plurals.progress_years, it.toInt(), it) },
        left = { Env.str(R.string.progress_left, it) },
        elapsed = { Env.str(R.string.progress_gone, it) },
        over = { Env.str(R.string.progress_over) },
    )

    @androidx.annotation.StringRes
    private fun name(of: Of): Int = when (of) {
        Of.DAY -> R.string.progress_day
        Of.WORKDAY -> R.string.progress_workday
        Of.WEEK -> R.string.progress_week
        Of.MONTH -> R.string.progress_month
        Of.YEAR -> R.string.progress_year
        Of.LIFE -> R.string.progress_life
    }

    private fun iconFor(of: Of): String = when (of) {
        Of.DAY -> Sym.TODAY
        Of.WORKDAY -> Sym.SCHEDULE
        Of.WEEK -> Sym.CALENDAR_TODAY
        Of.MONTH -> Sym.CALENDAR_MONTH
        Of.YEAR -> Sym.EVENT
        Of.LIFE -> Sym.FAVORITE
    }

    private class Reading(val elapsedFraction: Float, val remainingFraction: Float, val left: String, val elapsed: String)

    private fun read(of: Of, item: ItemConfig, now: ZonedDateTime, w: ProgressRules.Words = words): Reading {
        val ws = weekStart()
        val f = from.of(item)
        val t = to.of(item)
        val ym = yearMonth.of(item)
        val yd = yearDay.of(item)
        val ls = lifeStart.of(item)
        val le = lifeEnd.of(item)
        val frac = ProgressRules.fraction(of, now, f, t, ws, ym, yd, ls, le)
        val rem = (1f - frac).coerceIn(0f, 1f)
        return Reading(
            elapsedFraction = frac,
            remainingFraction = rem,
            left = ProgressRules.left(of, now, f, t, ws, w, ym, yd, ls, le),
            elapsed = ProgressRules.elapsed(of, now, f, t, ws, w, ym, yd, ls, le),
        )
    }

    private fun progressStyle(item: ItemConfig): ProgressStyle? = when (style.of(item)) {
        "wavy" -> ProgressStyle.WAVY
        "track" -> ProgressStyle.TRACK
        "ring" -> ProgressStyle.RING
        "pie" -> ProgressStyle.PIE
        else -> null
    }

    override fun state(item: ItemConfig): ItemState {
        val chosen = ProgressRules.of(of.of(item))
        val remainingMode = mode.of(item) == "remaining"
        val now = now()
        val r = read(chosen, item, now, spoken)
        val shownFrac = if (remainingMode) r.remainingFraction else r.elapsedFraction
        val percent = ProgressRules.percent(shownFrac)

        val pStyle = progressStyle(item)
        val barFractions = if (pStyle == null) null else {
            fun pick(ofType: Of): Float {
                val rd = if (ofType == chosen) r else read(ofType, item, now, spoken)
                return if (remainingMode) rd.remainingFraction else rd.elapsedFraction
            }
            when (bars.of(item)) {
                "day_year" -> listOf(pick(Of.DAY), pick(Of.YEAR))
                "all" -> listOf(pick(Of.DAY), pick(Of.YEAR), pick(Of.LIFE))
                else -> listOf(shownFrac)
            }
        }

        val graphic = if (pStyle != null && barFractions != null) {
            BarProgress(
                fractions = barFractions,
                style = pStyle,
                vertical = stack.of(item) == "vertical",
                widthDp = width.of(item),
                accentColor = tint.of(item) == "accent",
            )
        } else null

        return ItemState(
            icon = ProgressRules.icon(r.elapsedFraction),
            text = percent,
            widthKey = "progress",
            progress = graphic,
            desc = Env.str(R.string.progress_desc, Env.str(name(chosen)), percent, if (remainingMode) r.left else r.elapsed),
        )
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        val now = now()
        val chosen = ProgressRules.of(of.of(item))
        val remainingMode = mode.of(item) == "remaining"
        val includeLife = showLife.of(item) || chosen == Of.LIFE || bars.of(item) == "all"
        val rows = buildList {
            if (chosen == Of.WORKDAY) add(Of.WORKDAY)
            add(Of.DAY)
            add(Of.WEEK)
            add(Of.MONTH)
            add(Of.YEAR)
            if (includeLife) add(Of.LIFE)
        }

        val inf = rememberInfiniteTransition(label = "m3-wave")
        val wavePhase by inf.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )

        MenuCard(
            ProgressRules.icon(read(chosen, item, now).elapsedFraction),
            stringResource(R.string.item_progress_title),
            Dates.format("yMMMMEEEEd", now),
            trailing = {
                val onGlass = LocalMenuGlass.current?.blur == true
                val pillBorder = if (onGlass) lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, 0.35f).copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant
                val pillBg = if (onGlass) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.secondaryContainer
                val pillText = if (onGlass) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSecondaryContainer
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pillBg)
                        .border(1.dp, pillBorder, RoundedCornerShape(50))
                        .clickable {
                            Store.updateItem(item.id) { cur ->
                                val next = if (mode.of(cur) == "remaining") "elapsed" else "remaining"
                                mode.set(cur, next)
                            }
                            Ticker.refresh(item)
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(if (remainingMode) R.string.progress_remaining else R.string.progress_elapsed),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = pillText,
                    )
                }
            },
        ) {
            rows.forEachIndexed { i, period ->
                val r = read(period, item, now)
                val shownFrac = if (remainingMode) r.remainingFraction else r.elapsedFraction
                val accent = when (period) {
                    Of.DAY, Of.WORKDAY -> MaterialTheme.colorScheme.primary
                    Of.WEEK, Of.MONTH, Of.YEAR -> MaterialTheme.colorScheme.secondary
                    Of.LIFE -> MaterialTheme.colorScheme.tertiary
                }
                if (i > 0) Spacer(Modifier.height(8.dp))
                ExpressiveProgressCard(
                    icon = iconFor(period),
                    title = stringResource(name(period)),
                    highlighted = period == chosen,
                    fraction = shownFrac,
                    accentColor = accent,
                    wavePhase = wavePhase,
                    elapsedText = r.elapsed,
                    remainingText = r.left,
                    modifier = Modifier.fadeIn((i * 12).toFloat()),
                )
            }
            MenuDivider()
            MenuEntry(Sym.TUNE, stringResource(R.string.progress_preferences)) {
                host.openItemSettings(item.id)
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        val choices = Of.entries.map { ProgressRules.key(it) to stringResource(name(it)) }
        ChoiceRow(stringResource(R.string.progress_of), choices, of.of(item)) { set(of.set(item, it)) }

        ChoiceRow(
            stringResource(R.string.progress_mode),
            listOf(
                "elapsed" to stringResource(R.string.progress_elapsed),
                "remaining" to stringResource(R.string.progress_remaining),
            ),
            mode.of(item),
        ) { set(mode.set(item, it)) }

        ChoiceRow(
            stringResource(R.string.progress_style),
            listOf(
                "wavy" to stringResource(R.string.progress_style_wavy),
                "track" to stringResource(R.string.progress_style_track),
                "ring" to stringResource(R.string.progress_style_ring),
                "pie" to stringResource(R.string.progress_style_pie),
                "glyph" to stringResource(R.string.progress_style_glyph),
            ),
            style.of(item),
        ) { set(style.set(item, it)) }

        if (style.of(item) != "glyph") {
            ChoiceRow(
                stringResource(R.string.progress_bars),
                listOf(
                    "one" to stringResource(R.string.progress_bars_one),
                    "day_year" to stringResource(R.string.progress_bars_day_year),
                    "all" to stringResource(R.string.progress_bars_all),
                ),
                bars.of(item),
            ) { set(bars.set(item, it)) }

            if (bars.of(item) != "one" && style.of(item) in listOf("wavy", "track")) {
                ChoiceRow(
                    stringResource(R.string.progress_stack),
                    listOf(
                        "vertical" to stringResource(R.string.progress_stack_vertical),
                        "horizontal" to stringResource(R.string.progress_stack_horizontal),
                    ),
                    stack.of(item),
                ) { set(stack.set(item, it)) }
            }

            ChoiceRow(
                stringResource(R.string.progress_tint),
                listOf(
                    "accent" to stringResource(R.string.progress_tint_accent),
                    "bar" to stringResource(R.string.progress_tint_bar),
                ),
                tint.of(item),
            ) { set(tint.set(item, it)) }

            if (style.of(item) in listOf("wavy", "track")) {
                SliderRow(stringResource(R.string.progress_width), width.of(item), width.range, { "$it dp" }) {
                    set(width.set(item, it))
                }
            }
        }

        if (ProgressRules.of(of.of(item)) == Of.WORKDAY) {
            val h24 = DateFormat.is24HourFormat(LocalContext.current)
            val midnight = stringResource(R.string.progress_midnight)
            val hour = { h: Int -> if (h >= 24) midnight else Dates.format(Dates.timeSkeleton(h24), LocalDate.of(2000, 1, 1).atTime(h, 0).atZone(ZoneOffset.UTC)) }
            SliderRow(stringResource(R.string.progress_from), from.of(item), from.range, hour) { set(from.set(item, it)) }
            SliderRow(stringResource(R.string.progress_to), to.of(item), to.range, hour) { set(to.set(item, it)) }
        }

        if (ProgressRules.of(of.of(item)) == Of.YEAR || bars.of(item) != "one") {
            SliderRow(stringResource(R.string.progress_year_month), yearMonth.of(item), yearMonth.range, {
                Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }) { set(yearMonth.set(item, it)) }
            SliderRow(stringResource(R.string.progress_year_day), yearDay.of(item), yearDay.range) {
                set(yearDay.set(item, it))
            }
        }

        SwitchRow(
            stringResource(R.string.progress_show_life),
            showLife.of(item),
            help = stringResource(R.string.progress_show_life_help),
        ) { set(showLife.set(item, it)) }

        if (showLife.of(item) || ProgressRules.of(of.of(item)) == Of.LIFE || bars.of(item) == "all") {
            SliderRow(stringResource(R.string.progress_life_start), lifeStart.of(item), lifeStart.range) {
                set(lifeStart.set(item, it))
            }
            SliderRow(stringResource(R.string.progress_life_end), lifeEnd.of(item), lifeEnd.range) {
                set(lifeEnd.set(item, it))
            }
        }
    }

    // ---- for tests on a device (debug builds)

    override val stages = listOf("stage morning", "stage evening", "stage yearend")

    override fun debug(args: List<String>): String? = when {
        args.isEmpty() -> "progress: now=${now()} staged=${staged != null}"
        args == listOf("off") || args == listOf("stage", "off") -> { staged = null; Ticker.refresh(type); "progress: staging off" }
        args.size != 2 || args[0] != "stage" -> null
        else -> {
            val at = when (args[1]) {
                "morning" -> "2027-03-10T09:00"
                "evening" -> "2027-03-10T21:00"
                "yearend" -> "2027-12-31T23:00"
                else -> null
            }
            at?.let { staged = LocalDateTime.parse(it).atZone(ZoneId.systemDefault()); Ticker.refresh(type); "progress: staged ${args[1]}" }
        }
    }
}

@Composable
private fun ExpressiveProgressCard(
    icon: String,
    title: String,
    highlighted: Boolean,
    fraction: Float,
    accentColor: Color,
    wavePhase: Float,
    elapsedText: String,
    remainingText: String,
    modifier: Modifier = Modifier,
) {
    val pct = ProgressRules.percent(fraction)
    val onGlass = LocalMenuGlass.current?.blur == true
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(18.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val glassAccent = if (onGlass) lerp(accentColor, onSurface, 0.35f) else accentColor
    val cardFill = if (onGlass) {
        onSurface.copy(alpha = if (highlighted) (if (dark) 0.08f else 0.06f) else (if (dark) 0.04f else 0.03f))
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardRim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.28f else 0.72f),
            glassAccent.copy(alpha = if (highlighted) 0.52f else 0.28f),
        )
    )
    val badgeBg = glassAccent.copy(alpha = if (dark) 0.18f else 0.14f)
    val badgeBorder = glassAccent.copy(alpha = if (dark) 0.42f else 0.48f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardFill)
            .border(1.dp, cardRim, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .border(1.dp, badgeBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SymIcon(icon, size = 14.sp, filled = true, color = glassAccent)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeBg)
                    .border(1.dp, badgeBorder, RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    text = pct,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = onSurface,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        ExpressiveWavyIndicator(
            fraction = fraction,
            activeColor = glassAccent,
            trackColor = onSurface.copy(alpha = if (dark) 0.18f else 0.15f),
            phase = wavePhase,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        )
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = elapsedText,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = remainingText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = onSurface,
            )
        }
    }
}

@Composable
private fun ExpressiveWavyIndicator(
    fraction: Float,
    activeColor: Color,
    trackColor: Color,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val f = fraction.coerceIn(0f, 1f)
        val stroke = 3.2.dp.toPx()
        val amp = 2.4.dp.toPx()
        val wavelength = 22.dp.toPx()
        val gap = 4.dp.toPx()
        val stopR = 2.0.dp.toPx()
        val yMid = size.height / 2f
        val usable = (size.width - stopR * 2f).coerceAtLeast(4f)
        val activeEnd = (f * usable).coerceIn(0f, usable)

        if (f > 0.005f) {
            val path = Path()
            val step = 2f
            var x = 0f
            while (x <= activeEnd) {
                val y = yMid + sin((x / wavelength) * 2.0 * PI - phase).toFloat() * amp
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            val endY = yMid + sin((activeEnd / wavelength) * 2.0 * PI - phase).toFloat() * amp
            path.lineTo(activeEnd, endY)
            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        val trackStart = if (f > 0.005f) activeEnd + gap else 0f
        val trackEnd = size.width - stopR * 2.5f
        if (trackEnd - trackStart > stroke) {
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackStart, yMid - stroke / 2f),
                size = Size(trackEnd - trackStart, stroke),
                cornerRadius = CornerRadius(stroke / 2f),
            )
        }

        if (f < 0.985f) {
            drawCircle(
                color = activeColor,
                radius = stopR,
                center = Offset(size.width - stopR, yMid),
            )
        }
    }
}

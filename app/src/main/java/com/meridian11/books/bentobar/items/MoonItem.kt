package com.meridian11.books.bentobar.items

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.items.MoonRules.Phase
import com.meridian11.books.bentobar.ui.ChoiceRow
import com.meridian11.books.bentobar.ui.MenuCard
import com.meridian11.books.bentobar.ui.MenuDivider
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.SwitchRow
import com.meridian11.books.bentobar.ui.fadeIn
import com.meridian11.books.bentobar.ui.rememberTick
import com.meridian11.books.bentobar.util.Dates
import com.meridian11.books.bentobar.util.Now
import com.meridian11.books.bentobar.util.Sym
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Moon phase: current lunar phase symbol in the bar (mirrored for the Southern Hemisphere),
 * illumination percentage, moon age in days, and countdown to the next major phase.
 * Completely offline: uses [MoonRules] + [MoonData] and shades a bundled public-domain NASA LRO
 * full-moon photograph with spherical terminator lighting.
 */
object MoonItem : ItemType("moon", R.string.item_moon_title, Sym.NIGHTS_STAY, R.string.item_moon_desc) {
    override val group = ItemGroup.TIME
    override val refreshMs = 60_000L
    override val menuWidthDp = 340
    override val canBeActive = true
    override val trigger = Trigger(R.string.trigger_moon, R.string.trigger_moon_short)

    internal val show = Opt.oneOf("show", "icon", "icon", "pct", "phase", "both")
    internal val southern = Opt.bool("southern", false)

    private const val NASA_MOON_URL = "https://science.nasa.gov/moon/moon-phases/"

    private val barBitmaps = HashMap<String, Bitmap>()
    @Volatile private var basePhoto: Bitmap? = null
    @Volatile private var shadedKey: String? = null
    @Volatile private var shadedBitmap: Bitmap? = null

    @StringRes
    private fun phaseRes(p: Phase): Int = when (p) {
        Phase.NEW_MOON -> R.string.moon_phase_new
        Phase.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
        Phase.FIRST_QUARTER -> R.string.moon_phase_first_quarter
        Phase.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
        Phase.FULL_MOON -> R.string.moon_phase_full
        Phase.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
        Phase.LAST_QUARTER -> R.string.moon_phase_last_quarter
        Phase.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
    }

    @DrawableRes
    private fun drawableRes(p: Phase): Int = when (p) {
        Phase.NEW_MOON -> R.drawable.ic_moon_new
        Phase.WAXING_CRESCENT -> R.drawable.ic_moon_waxing_crescent
        Phase.FIRST_QUARTER -> R.drawable.ic_moon_first_quarter
        Phase.WAXING_GIBBOUS -> R.drawable.ic_moon_waxing_gibbous
        Phase.FULL_MOON -> R.drawable.ic_moon_full
        Phase.WANING_GIBBOUS -> R.drawable.ic_moon_waning_gibbous
        Phase.LAST_QUARTER -> R.drawable.ic_moon_last_quarter
        Phase.WANING_CRESCENT -> R.drawable.ic_moon_waning_crescent
    }

    private fun barBitmap(p: Phase, south: Boolean): Bitmap? {
        val flip = south && p != Phase.NEW_MOON && p != Phase.FULL_MOON
        val key = "${p.name}:$flip"
        synchronized(barBitmaps) {
            barBitmaps[key]?.let { return it }
            val d = ContextCompat.getDrawable(Env.app, drawableRes(p)) ?: return null
            val px = 64
            val b = createBitmap(px, px)
            val canvas = Canvas(b)
            if (flip) canvas.scale(-1f, 1f, px / 2f, px / 2f)
            d.setBounds(0, 0, px, px)
            d.draw(canvas)
            barBitmaps[key] = b
            return b
        }
    }

    private fun fullMoonPhoto(): Bitmap? {
        basePhoto?.let { return it }
        return runCatching {
            BitmapFactory.decodeResource(Env.app.resources, R.drawable.moon_full_photo)
        }.getOrNull()?.also { basePhoto = it }
    }

    /**
     * Renders the public-domain NASA LRO lunar photograph with realistic 3D spherical terminator
     * shading and faint earthshine on the night side for the current illumination fraction.
     */
    private fun shadedLunarPhoto(illuminationPct: Double, waxing: Boolean): Bitmap? {
        val k = (illuminationPct / 100.0).coerceIn(0.0, 1.0)
        val bucket = (k * 100.0).roundToInt()
        val key = "$bucket:$waxing"
        if (shadedKey == key) shadedBitmap?.let { return it }

        val base = fullMoonPhoto() ?: return null
        val w = base.width
        val h = base.height
        val src = IntArray(w * h)
        base.getPixels(src, 0, w, 0, 0, w, h)

        val cosPhi = (2.0 * k - 1.0).coerceIn(-1.0, 1.0)
        val sinPhi = sqrt((1.0 - cosPhi * cosPhi).coerceAtLeast(0.0)) * if (waxing) 1.0 else -1.0
        val halfW = w / 2.0
        val halfH = h / 2.0
        val rPix = min(w, h) / 2.0 - 2.0
        val out = IntArray(w * h)

        for (y in 0 until h) {
            val ny = (y - halfH) / rPix
            val rowOff = y * w
            for (x in 0 until w) {
                val px = src[rowOff + x]
                val a = (px ushr 24) and 0xFF
                if (a <= 2) {
                    out[rowOff + x] = 0
                    continue
                }
                val nx = (x - halfW) / rPix
                val r2 = nx * nx + ny * ny
                if (r2 > 1.0) {
                    out[rowOff + x] = px
                    continue
                }
                val nz = sqrt((1.0 - r2).coerceAtLeast(0.0))
                val ndotl = nx * sinPhi + nz * cosPhi
                val t = ((ndotl + 0.08) / 0.16).coerceIn(0.0, 1.0)
                val smooth = t * t * (3.0 - 2.0 * t)
                val shade = 0.13 + 0.87 * smooth

                val r = ((px ushr 16) and 0xFF) * shade
                val g = ((px ushr 8) and 0xFF) * shade
                val b = (px and 0xFF) * shade
                out[rowOff + x] = (a shl 24) or
                    (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (g.roundToInt().coerceIn(0, 255) shl 8) or
                    b.roundToInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888).also {
            shadedKey = key
            shadedBitmap = it
        }
    }

    private fun formatDecimal(value: Double): String {
        val v = if (value < 0.1) 0.0 else (value * 10.0).roundToInt() / 10.0
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            if (v % 1.0 == 0.0) {
                minimumFractionDigits = 0
                maximumFractionDigits = 0
            } else {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }
        }
        return nf.format(v)
    }

    override fun state(item: ItemConfig): ItemState {
        val nowMs = Now.wall()
        val lunar = MoonRules.compute(nowMs)
        val south = southern.of(item)
        val phaseName = Env.str(phaseRes(lunar.phase))
        val pctInt = lunar.illuminationPct.roundToInt()
        val pctStr = formatDecimal(lunar.illuminationPct)

        val text: String? = when (show.of(item)) {
            "pct" -> "$pctInt%"
            "phase" -> phaseName
            "both" -> "$phaseName $pctInt%"
            else -> null
        }

        val desc = Env.str(R.string.moon_desc, phaseName, pctStr)
        return ItemState(
            icon = Sym.NIGHTS_STAY,
            image = barBitmap(lunar.phase, south),
            tintImage = true,
            text = text,
            desc = desc,
            tooltip = desc,
            active = lunar.isExactMajor && lunar.phase.majorHighlight,
            widthKey = if (show.of(item) == "pct") "moon-pct" else null,
        )
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        val nowMs = Now.wall()
        val lunar = MoonRules.compute(nowMs)
        val south = southern.of(item)
        val zone = ZoneId.systemDefault()
        val h24 = DateFormat.is24HourFormat(Env.app)
        val timeSkeleton = Dates.timeSkeleton(h24)
        val dateTimeSkeleton = "dMMM" + timeSkeleton

        val phaseName = stringResource(phaseRes(lunar.phase))
        val nextName = stringResource(phaseRes(lunar.nextMajor))
        val exactTime = if (lunar.isExactMajor && lunar.exactMajorEpochMs != null) {
            stringResource(R.string.moon_exact_at, Dates.format(timeSkeleton, lunar.exactMajorEpochMs, zone))
        } else null

        val daysDiff = MoonRules.dayDifference(nowMs, lunar.nextMajorEpochMs, zone)
        val nextLine = when {
            daysDiff <= 0 -> stringResource(R.string.moon_next_today, Dates.format(timeSkeleton, lunar.nextMajorEpochMs, zone))
            daysDiff == 1 -> stringResource(R.string.moon_next_tomorrow, Dates.format(timeSkeleton, lunar.nextMajorEpochMs, zone))
            else -> pluralStringResource(R.plurals.moon_next_in_days, daysDiff, daysDiff, Dates.format(dateTimeSkeleton, lunar.nextMajorEpochMs, zone))
        }

        val photo = remember(lunar.illuminationPct.roundToInt(), lunar.phase.waxing) {
            shadedLunarPhoto(lunar.illuminationPct, lunar.phase.waxing)
        }

        MenuCard(
            icon = Sym.NIGHTS_STAY,
            title = stringResource(R.string.item_moon_title),
            iconRes = drawableRes(lunar.phase),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        host.close()
                        Env.launch(Intent(Intent.ACTION_VIEW, NASA_MOON_URL.toUri()))
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .fadeIn(10f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), CircleShape)
                        .then(if (south) Modifier.graphicsLayer(rotationZ = 180f) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = phaseName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = phaseName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (exactTime != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = exactTime,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.moon_illumination, formatDecimal(lunar.illuminationPct)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.moon_age, formatDecimal(lunar.ageDays)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                    )
                    Text(
                        text = nextName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = nextLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MenuDivider()
            MenuEntry(Sym.OPEN_IN_NEW, stringResource(R.string.moon_open_nasa)) {
                host.close()
                Env.launch(Intent(Intent.ACTION_VIEW, NASA_MOON_URL.toUri()))
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow(
            stringResource(R.string.moon_show_label),
            listOf(
                "icon" to stringResource(R.string.moon_show_icon),
                "pct" to stringResource(R.string.moon_show_pct),
                "phase" to stringResource(R.string.moon_show_phase),
                "both" to stringResource(R.string.moon_show_both),
            ),
            show.of(item),
        ) { set(show.set(item, it)) }

        SwitchRow(
            stringResource(R.string.moon_southern_label),
            southern.of(item),
            help = stringResource(R.string.moon_southern_help),
        ) { set(southern.set(item, it)) }
    }
}

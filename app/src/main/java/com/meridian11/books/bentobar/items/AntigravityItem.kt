package com.meridian11.books.bentobar.items

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Kept
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.ui.ChoiceRow
import com.meridian11.books.bentobar.ui.MenuCard
import com.meridian11.books.bentobar.ui.MenuDivider
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.fadeIn
import com.meridian11.books.bentobar.ui.rememberTick
import com.meridian11.books.bentobar.util.Now
import com.meridian11.books.bentobar.util.Sym
import org.json.JSONObject
import java.io.File
import android.provider.Settings
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Antigravity IDE quota usage and reset timers.
 * Reads live quota data from the local Antigravity sync daemon (http://127.0.0.1:18765/quota)
 * or shared usage_limits.json, persisting the latest snapshot locally so reset countdowns
 * stay accurate even while Antigravity is closed.
 */
object AntigravityItem : ItemType(
    type = "antigravity",
    titleRes = R.string.item_antigravity_title,
    icon = Sym.ANTIGRAVITY,
    blurbRes = R.string.item_antigravity_desc,
) {
    override val refreshMs = 5_000L
    override val menuWidthDp = 332
    override val canBeActive = true

    private val belowPct = Threshold("below", 25, 5..80, step = 5) { "$it%" }
    override val trigger = Trigger(R.string.trigger_antigravity, R.string.trigger_antigravity_short, belowPct)

    private const val GLOBAL_SETTING_KEY = "antigravity_quota"
    private const val SHARED_JSON_PATH = "/storage/emulated/10/Documents/Projects/.gemini/antigravity/usage_limits.json"
    private const val KEPT_KEY = "antigravity_quota"
    private const val ANTIGRAVITY_PKG = "com.google.android.apps.antigravity"

    data class QuotaBucket(
        val bucketId: String,
        val window: String, // "weekly" or "5h"
        val remainingFraction: Float,
        val remainingPercent: Int,
        val resetEpochMs: Long,
        val fallbackCountdown: String,
    ) {
        fun countdown(nowMs: Long): String {
            if (resetEpochMs > nowMs) {
                val diff = resetEpochMs - nowMs
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                val mins = (TimeUnit.MILLISECONDS.toMinutes(diff) % 60).coerceAtLeast(if (days == 0L && hours == 0L) 1L else 0L)
                return when {
                    days > 0 -> "${days}d ${hours}h"
                    hours > 0 -> "${hours}h ${mins}m"
                    else -> "${mins}m"
                }
            }
            return fallbackCountdown.ifBlank { "—" }
        }
    }

    data class QuotaGroup(
        val displayName: String,
        val shortName: String,
        val buckets: List<QuotaBucket>,
    )

    data class QuotaSnapshot(
        val activeModelLabel: String,
        val activeGroupName: String,
        val planName: String,
        val groups: List<QuotaGroup>,
        val fetchedAtMs: Long,
    )

    private val loader: Refresher<String, QuotaSnapshot> = Background.refresher(
        type = type,
        every = { _, _ -> 15_000L },
        load = { _, last -> fetchSnapshot(last) },
    )

    private fun fetchSnapshot(last: QuotaSnapshot?): QuotaSnapshot {
        val now = Now.wall()
        val rawText = fetchFromLocalDaemon()
            ?: readFromSharedFile()
            ?: Kept.own("antigravity").read(KEPT_KEY)?.text
        if (rawText != null) {
            parseSnapshot(rawText, now)?.let { parsed ->
                Kept.own("antigravity").write(KEPT_KEY, rawText, now)
                return parsed
            }
        }
        return last ?: Kept.own("antigravity").read(KEPT_KEY)?.text?.let { parseSnapshot(it, now) } ?: defaultSnapshot(now)
    }

    private fun fetchFromLocalDaemon(): String? = runCatching {
        Settings.Global.getString(Env.app.contentResolver, GLOBAL_SETTING_KEY)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun readFromSharedFile(): String? = runCatching {
        val f = File(SHARED_JSON_PATH)
        if (f.isFile && f.canRead()) f.readText() else null
    }.getOrNull()

    private fun parseIsoMs(iso: String): Long = runCatching {
        if (iso.isBlank()) 0L else Instant.parse(iso.trim()).toEpochMilli()
    }.getOrDefault(0L)

    private fun shortGroup(name: String): String = when {
        name.contains("Argon", ignoreCase = true) -> "Argon"
        name.contains("Claude", ignoreCase = true) || name.contains("GPT", ignoreCase = true) -> "Claude/GPT"
        else -> "Gemini"
    }

    internal fun parseSnapshot(text: String, nowMs: Long): QuotaSnapshot? = runCatching {
        val root = JSONObject(text)
        val groupsArr = root.optJSONArray("groups") ?: return null
        val groups = mutableListOf<QuotaGroup>()
        for (i in 0 until groupsArr.length()) {
            val gObj = groupsArr.optJSONObject(i) ?: continue
            val gName = gObj.optString("displayName", "Model Group")
            val bArr = gObj.optJSONArray("buckets") ?: continue
            val buckets = mutableListOf<QuotaBucket>()
            for (j in 0 until bArr.length()) {
                val bObj = bArr.optJSONObject(j) ?: continue
                val frac = bObj.optDouble("remainingFraction", 1.0).toFloat().coerceIn(0f, 1f)
                val pct = bObj.optInt("remainingPercent", (frac * 100f).roundToInt()).coerceIn(0, 100)
                val resetIso = bObj.optString("resetTime", "")
                val win = bObj.optString("window", if (bObj.optString("bucketId").contains("5h")) "5h" else "weekly")
                buckets.add(
                    QuotaBucket(
                        bucketId = bObj.optString("bucketId", ""),
                        window = win,
                        remainingFraction = frac,
                        remainingPercent = pct,
                        resetEpochMs = parseIsoMs(resetIso),
                        fallbackCountdown = bObj.optString("resetCountdown", "—"),
                    )
                )
            }
            // Match the screenshot order: Weekly limit first, then 5h limit.
            val sorted = buckets.sortedBy { if (it.window.equals("weekly", ignoreCase = true)) 0 else 1 }
            if (sorted.isNotEmpty()) {
                groups.add(QuotaGroup(displayName = gName, shortName = shortGroup(gName), buckets = sorted))
            }
        }
        if (groups.isEmpty()) return null
        QuotaSnapshot(
            activeModelLabel = root.optString("activeModelLabel", "Gemini 4 Argon"),
            activeGroupName = root.optString("activeGroupName", "Gemini 4 Argon"),
            planName = root.optString("planName", "Pro"),
            groups = groups,
            fetchedAtMs = nowMs,
        )
    }.getOrNull()

    private fun defaultSnapshot(nowMs: Long): QuotaSnapshot = QuotaSnapshot(
        activeModelLabel = "Gemini 4 Argon (Medium)",
        activeGroupName = "Gemini 4 Argon",
        planName = "Pro",
        groups = listOf(
            QuotaGroup(
                displayName = "Gemini Models",
                shortName = "Gemini",
                buckets = listOf(
                    QuotaBucket("gemini-weekly", "weekly", 0.98f, 98, nowMs + 590_400_000L, "6d 20h"),
                    QuotaBucket("gemini-5h", "5h", 0.96f, 96, nowMs + 6_840_000L, "1h 54m"),
                ),
            ),
            QuotaGroup(
                displayName = "Claude and GPT models",
                shortName = "Claude/GPT",
                buckets = listOf(
                    QuotaBucket("3p-weekly", "weekly", 0.16f, 16, nowMs + 187_200_000L, "2d 4h"),
                    QuotaBucket("3p-5h", "5h", 0.88f, 88, nowMs + 5_940_000L, "1h 39m"),
                ),
            ),
            QuotaGroup(
                displayName = "Gemini 4 Argon",
                shortName = "Argon",
                buckets = listOf(
                    QuotaBucket("gemini-argon-wtu-weekly", "weekly", 0.46f, 46, nowMs + 453_600_000L, "5d 6h"),
                ),
            ),
        ),
        fetchedAtMs = nowMs,
    )

    internal fun currentSnapshot(ask: Boolean = true): QuotaSnapshot {
        if (ask) loader.want("live")
        return loader.peek("live")
            ?: Kept.own("antigravity").read(KEPT_KEY)?.text?.let { parseSnapshot(it, Now.wall()) }
            ?: defaultSnapshot(Now.wall())
    }

    internal fun refreshNow() {
        loader.refresh("live", floorMs = 1_000L)
    }

    internal fun openApp() {
        val pm = Env.app.packageManager
        val intent = pm.getLaunchIntentForPackage(ANTIGRAVITY_PKG)
        if (intent != null) Env.launch(intent)
    }

    private fun pickBucket(group: QuotaGroup, winPref: String): QuotaBucket {
        return when (winPref) {
            "5h" -> group.buckets.firstOrNull { it.window.equals("5h", ignoreCase = true) } ?: group.buckets.first()
            "weekly" -> group.buckets.firstOrNull { it.window.equals("weekly", ignoreCase = true) } ?: group.buckets.first()
            else -> group.buckets.minByOrNull { it.remainingPercent } ?: group.buckets.first()
        }
    }

    private fun formatBarText(group: QuotaGroup, bucket: QuotaBucket, show: String, nowMs: Long): String {
        val cd = bucket.countdown(nowMs)
        return when (show) {
            "pct" -> "${bucket.remainingPercent}%"
            "full" -> "${group.shortName} ${bucket.remainingPercent}% · $cd"
            else -> "${bucket.remainingPercent}% · $cd"
        }
    }

    override fun state(item: ItemConfig): ItemState {
        val snap = currentSnapshot(ask = item.section != Section.OFF)
        val nowMs = Now.wall()
        val groupPref = item.opt("group", "active")
        val winPref = item.opt("window", "lowest")
        val showPref = item.opt("show", "both")

        val chosenPair: Pair<QuotaGroup, QuotaBucket> = when (groupPref) {
            "lowest" -> {
                val allPairs = snap.groups.flatMap { g -> g.buckets.map { b -> g to b } }
                allPairs.minByOrNull { it.second.remainingPercent } ?: (snap.groups.first() to snap.groups.first().buckets.first())
            }
            "turns" -> {
                val idx = ((Now.elapsed() / 5_000L).toInt().coerceAtLeast(0)) % snap.groups.size
                val g = snap.groups[idx]
                g to pickBucket(g, winPref)
            }
            "gemini" -> {
                val g = snap.groups.firstOrNull { it.displayName.contains("Gemini Models", ignoreCase = true) } ?: snap.groups.first()
                g to pickBucket(g, winPref)
            }
            "claude" -> {
                val g = snap.groups.firstOrNull { it.displayName.contains("Claude", ignoreCase = true) } ?: snap.groups.first()
                g to pickBucket(g, winPref)
            }
            "argon" -> {
                val g = snap.groups.firstOrNull { it.displayName.contains("Argon", ignoreCase = true) } ?: snap.groups.last()
                g to pickBucket(g, winPref)
            }
            else -> { // "active"
                val g = snap.groups.firstOrNull { it.displayName.equals(snap.activeGroupName, ignoreCase = true) }
                    ?: snap.groups.firstOrNull { snap.activeModelLabel.contains(it.shortName, ignoreCase = true) }
                    ?: snap.groups.first()
                g to pickBucket(g, winPref)
            }
        }

        val (group, bucket) = chosenPair
        val text = formatBarText(group, bucket, showPref, nowMs)
        val turnsList = if (groupPref == "turns") {
            snap.groups.map { g -> formatBarText(g, pickBucket(g, winPref), showPref, nowMs) }
        } else null

        val minPctAll = snap.groups.flatMap { it.buckets }.minOfOrNull { it.remainingPercent } ?: bucket.remainingPercent
        val tone = when {
            bucket.remainingPercent <= 10 -> Tone.ALERT
            bucket.remainingPercent <= 20 -> Tone.WARN
            else -> Tone.NORMAL
        }
        val threshold = belowPct.of(item)
        val isActive = minPctAll <= threshold

        val desc = "${group.displayName}: ${bucket.remainingPercent}% left, resets in ${bucket.countdown(nowMs)}"
        return ItemState(
            icon = Sym.ANTIGRAVITY,
            text = text,
            desc = desc,
            tooltip = desc,
            active = isActive,
            tone = tone,
            widthKey = if (turnsList == null) "agy-$groupPref-$winPref-$showPref" else null,
            turns = turnsList,
        )
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        rememberTick()
        val snap = currentSnapshot(ask = true)
        val nowMs = Now.wall()

        MenuCard(
            icon = Sym.ANTIGRAVITY,
            title = stringResource(R.string.antigravity_menu_title),
        ) {
            snap.groups.forEachIndexed { idx, group ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (idx == 0) 2.dp else 12.dp, bottom = 2.dp)
                        .fadeIn((idx * 18).toFloat())
                ) {
                    Text(
                        text = group.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    group.buckets.forEachIndexed { bIdx, bucket ->
                        val winLabel = if (bucket.window.equals("5h", ignoreCase = true)) {
                            stringResource(R.string.antigravity_5h_limit)
                        } else {
                            stringResource(R.string.antigravity_weekly_limit)
                        }
                        val pctText = stringResource(R.string.antigravity_pct_left, bucket.remainingPercent)
                        val resetText = stringResource(R.string.antigravity_resets_in, bucket.countdown(nowMs))

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (bIdx == 0 && group.buckets.size > 1) 4.dp else 2.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = winLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(top = 1.dp),
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pctText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    QuotaRing(
                                        remainingFraction = bucket.remainingFraction,
                                        remainingPercent = bucket.remainingPercent,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = resetText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            MenuDivider()
            MenuEntry(Sym.REFRESH, stringResource(R.string.common_refresh)) { refreshNow() }
            MenuEntry(Sym.ANTIGRAVITY, stringResource(R.string.antigravity_open_app)) {
                host.close()
                openApp()
            }
        }
    }

    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set ->
        ChoiceRow(
            stringResource(R.string.antigravity_group_label),
            listOf(
                "active" to stringResource(R.string.antigravity_group_active),
                "lowest" to stringResource(R.string.antigravity_group_lowest),
                "turns" to stringResource(R.string.antigravity_group_turns),
                "gemini" to stringResource(R.string.antigravity_group_gemini),
                "claude" to stringResource(R.string.antigravity_group_claude),
                "argon" to stringResource(R.string.antigravity_group_argon),
            ),
            item.opt("group", "active"),
        ) { set(item.with("group", it.takeIf { v -> v != "active" })) }

        ChoiceRow(
            stringResource(R.string.antigravity_window_label),
            listOf(
                "lowest" to stringResource(R.string.antigravity_window_lowest),
                "5h" to stringResource(R.string.antigravity_window_5h),
                "weekly" to stringResource(R.string.antigravity_window_weekly),
            ),
            item.opt("window", "lowest"),
        ) { set(item.with("window", it.takeIf { v -> v != "lowest" })) }

        ChoiceRow(
            stringResource(R.string.antigravity_show_label),
            listOf(
                "both" to stringResource(R.string.antigravity_show_both),
                "pct" to stringResource(R.string.antigravity_show_pct),
                "full" to stringResource(R.string.antigravity_show_full),
            ),
            item.opt("show", "both"),
        ) { set(item.with("show", it.takeIf { v -> v != "both" })) }
    }
}

/**
 * Circular quota ring matching the Antigravity Usage popup:
 * subtle circular track + colored remaining arc starting at 12 o'clock (-90°).
 */
@Composable
private fun QuotaRing(remainingFraction: Float, remainingPercent: Int) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    val arcColor = when {
        remainingPercent <= 10 -> Color(0xFFEF4444) // Red
        remainingPercent <= 20 -> Color(0xFFF59E0B) // Orange/Amber (matches 16% left in screenshot)
        else -> Color(0xFF4CAF50)                   // Green (matches 98%, 96%, 88%, 46% left in screenshot)
    }
    Canvas(Modifier.size(18.dp)) {
        val strokePx = 2.3.dp.toPx()
        val inset = strokePx / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx),
        )
        val sweep = (remainingFraction.coerceIn(0f, 1f) * 360f)
        if (sweep > 0.5f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

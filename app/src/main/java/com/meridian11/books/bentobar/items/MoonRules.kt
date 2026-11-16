package com.meridian11.books.bentobar.items

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure lunar phase arithmetic from [MoonData] (2026–2031 UTC major phases):
 * phase classification, exact-day window (±12h), trigonometric illumination curve,
 * lunar age in days since New Moon, and next major phase timing.
 */
object MoonRules {
    enum class Phase(val key: String) {
        NEW_MOON("New Moon"),
        WAXING_CRESCENT("Waxing Crescent"),
        FIRST_QUARTER("First Quarter"),
        WAXING_GIBBOUS("Waxing Gibbous"),
        FULL_MOON("Full Moon"),
        WANING_GIBBOUS("Waning Gibbous"),
        LAST_QUARTER("Last Quarter"),
        WANING_CRESCENT("Waning Crescent");

        val waxing: Boolean
            get() = this == NEW_MOON || this == WAXING_CRESCENT || this == FIRST_QUARTER || this == WAXING_GIBBOUS

        val majorHighlight: Boolean
            get() = this == NEW_MOON || this == FULL_MOON
    }

    data class State(
        val phase: Phase,
        val isExactMajor: Boolean,
        val exactMajorEpochMs: Long?,
        val illuminationPct: Double,
        val ageDays: Double,
        val nextMajor: Phase,
        val nextMajorEpochMs: Long,
    )

    private const val MS_PER_DAY = 86_400_000.0
    private const val TWELVE_HOURS_MS = 12 * 3_600_000L
    private const val SYNODIC_MONTH_DAYS = 29.530588853

    private fun phaseOf(name: String): Phase = when (name) {
        "New Moon" -> Phase.NEW_MOON
        "First Quarter" -> Phase.FIRST_QUARTER
        "Full Moon" -> Phase.FULL_MOON
        "Last Quarter" -> Phase.LAST_QUARTER
        else -> Phase.WAXING_CRESCENT
    }

    fun compute(nowMs: Long): State {
        val list = MoonData.phases
        val lastNew = list.lastOrNull { it.name == "New Moon" && it.epochMs <= nowMs }
        val ageDays = if (lastNew != null) {
            ((nowMs - lastNew.epochMs) / MS_PER_DAY).coerceAtLeast(0.0)
        } else {
            val firstNew = list.firstOrNull { it.name == "New Moon" }?.epochMs ?: nowMs
            val diffDays = (firstNew - nowMs) / MS_PER_DAY
            (SYNODIC_MONTH_DAYS - (diffDays % SYNODIC_MONTH_DAYS)) % SYNODIC_MONTH_DAYS
        }

        val exact = list.firstOrNull { abs(nowMs - it.epochMs) <= TWELVE_HOURS_MS }
        val nextIdx = list.indexOfFirst { it.epochMs > nowMs }

        val (prev, next) = when {
            nextIdx <= 0 -> list.first() to list[1]
            nextIdx >= list.size -> list[list.size - 2] to list.last()
            else -> list[nextIdx - 1] to list[nextIdx]
        }

        val intermediate = when ("${prev.name}->${next.name}") {
            "New Moon->First Quarter" -> Phase.WAXING_CRESCENT
            "First Quarter->Full Moon" -> Phase.WAXING_GIBBOUS
            "Full Moon->Last Quarter" -> Phase.WANING_GIBBOUS
            "Last Quarter->New Moon" -> Phase.WANING_CRESCENT
            else -> Phase.WAXING_CRESCENT
        }

        val current = exact?.let { phaseOf(it.name) } ?: intermediate

        val (illumPrev, illumNext) = if (exact != null) {
            val afterIdx = list.indexOfFirst { it.epochMs > exact.epochMs }
            if (afterIdx >= 0) exact to list[afterIdx] else prev to next
        } else {
            prev to next
        }
        val span = (illumNext.epochMs - illumPrev.epochMs).coerceAtLeast(1L).toDouble()
        val p = ((nowMs - illumPrev.epochMs).toDouble() / span).coerceIn(0.0, 1.0)
        val illumination = when ("${illumPrev.name}->${illumNext.name}") {
            "New Moon->First Quarter" -> 50.0 * (1.0 - cos(PI * p))
            "First Quarter->Full Moon" -> 50.0 + 50.0 * sin(PI * p / 2.0)
            "Full Moon->Last Quarter" -> 100.0 - 50.0 * (1.0 - cos(PI * p))
            "Last Quarter->New Moon" -> 50.0 - 50.0 * sin(PI * p / 2.0)
            else -> 50.0
        }.coerceIn(0.0, 100.0)

        val firstFutureIdx = list.indexOfFirst { it.epochMs > nowMs }.takeIf { it >= 0 } ?: (list.size - 1)
        val firstFuture = list[firstFutureIdx]
        val targetNext = if (firstFuture.epochMs - nowMs >= TWELVE_HOURS_MS) {
            firstFuture
        } else {
            list.getOrElse(firstFutureIdx + 1) { firstFuture }
        }

        val nextMajor = when (current) {
            Phase.NEW_MOON, Phase.WAXING_CRESCENT -> Phase.FIRST_QUARTER
            Phase.FIRST_QUARTER, Phase.WAXING_GIBBOUS -> Phase.FULL_MOON
            Phase.FULL_MOON, Phase.WANING_GIBBOUS -> Phase.LAST_QUARTER
            Phase.LAST_QUARTER, Phase.WANING_CRESCENT -> Phase.NEW_MOON
        }

        return State(
            phase = current,
            isExactMajor = exact != null,
            exactMajorEpochMs = exact?.epochMs,
            illuminationPct = illumination,
            ageDays = ageDays,
            nextMajor = nextMajor,
            nextMajorEpochMs = targetNext.epochMs,
        )
    }

    /** Days difference in [zone] between [nowMs] and [targetMs] for "Today" / "Tomorrow" / "in N days". */
    fun dayDifference(nowMs: Long, targetMs: Long, zone: ZoneId): Int {
        val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val targetDay: LocalDate = Instant.ofEpochMilli(targetMs).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, targetDay).toInt()
    }
}

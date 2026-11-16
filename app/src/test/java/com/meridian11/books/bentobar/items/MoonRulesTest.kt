package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.MoonRules.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MoonRulesTest {
    private fun utc(text: String): Long = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun exactMajorPhasesAreDetectedWithinTwelveHours() {
        val newMoon = MoonRules.compute(utc("2026-11-09T07:02:00"))
        assertEquals(Phase.NEW_MOON, newMoon.phase)
        assertTrue(newMoon.isExactMajor)
        assertTrue(newMoon.illuminationPct < 1.0)
        assertEquals(Phase.FIRST_QUARTER, newMoon.nextMajor)

        val fullMoon = MoonRules.compute(utc("2026-11-24T14:53:00"))
        assertEquals(Phase.FULL_MOON, fullMoon.phase)
        assertTrue(fullMoon.isExactMajor)
        assertTrue(fullMoon.illuminationPct > 99.0)
        assertEquals(Phase.LAST_QUARTER, fullMoon.nextMajor)
    }

    @Test fun intermediatePhasesProgressSmoothlyBetweenMajorEvents() {
        val crescent = MoonRules.compute(utc("2026-11-11T09:00:00"))
        assertEquals(Phase.WAXING_CRESCENT, crescent.phase)
        assertFalse(crescent.isExactMajor)
        assertTrue(crescent.phase.waxing)
        assertTrue(crescent.illuminationPct in 5.0..48.0)
        assertTrue(crescent.ageDays in 1.8..2.3)

        val gibbous = MoonRules.compute(utc("2026-11-21T00:00:00"))
        assertEquals(Phase.WAXING_GIBBOUS, gibbous.phase)
        assertTrue(gibbous.illuminationPct in 55.0..95.0)
    }

    @Test fun dayDifferenceUsesCalendarDatesInZone() {
        assertEquals(0, MoonRules.dayDifference(utc("2026-11-16T05:00:00"), utc("2026-11-16T22:00:00"), ZoneOffset.UTC))
        assertEquals(1, MoonRules.dayDifference(utc("2026-11-16T22:00:00"), utc("2026-11-17T08:00:00"), ZoneOffset.UTC))
        assertEquals(8, MoonRules.dayDifference(utc("2026-11-16T10:00:00"), utc("2026-11-24T14:53:00"), ZoneOffset.UTC))
    }
}

package com.meridian11.books.bentobar.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** A share as a whole percentage: what the system shows for the same reading, never one less because of how a fraction is stored. */
class FmtPercentTest {
    @Test fun aBatteryLevelReadsAsTheSystemShowsIt() {
        // The system's percentage is level × 100 / scale in whole numbers; 29 / 100 as a double times 100 is 28.999…
        for (scale in listOf(100, 255, 1000)) for (lvl in 0..scale) {
            assertEquals("$lvl of $scale", Fmt.percent(lvl * 100 / scale), Fmt.percent(lvl.toDouble() / scale))
        }
    }

    @Test fun aNotificationsProgressReadsAsItsWholePercentage() {
        // Progress is a Float (0.58f is 0.57999998…), widened to a double.
        for (i in 0..100) assertEquals("$i", Fmt.percent(i), Fmt.percent((i / 100f).toDouble()))
        for (i in 0..100) assertEquals("$i of 200", Fmt.percent(i / 2), Fmt.percent((i.toFloat() / 200).toDouble()))
    }

    @Test fun whatIsNotYetAWholePercentIsTheOneBelow() {
        assertEquals("99%", Fmt.percent(0.996))
        assertEquals("28%", Fmt.percent(0.2899))
        assertEquals("0%", Fmt.percent(0.004))
        assertEquals("100%", Fmt.percent(1.0))
    }
}

package com.meridian11.books.bentobar.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockJumpTest {
    private val start = ClockAnchor(wall = 1_000_000_000L, elapsed = 50_000L, boot = 7)

    @Test fun timePassingIsNotAJump() {
        assertEquals(0L, start.jump(ClockAnchor(start.wall + 600_000, start.elapsed + 600_000, 7)))
    }

    @Test fun settingTheClockAheadIsMeasured() {
        // Ten minutes passed, and the clock was set an hour ahead.
        assertEquals(3_600_000L, start.jump(ClockAnchor(start.wall + 600_000 + 3_600_000, start.elapsed + 600_000, 7)))
    }

    @Test fun settingTheClockBackIsMeasured() {
        assertEquals(-3_600_000L, start.jump(ClockAnchor(start.wall + 60_000 - 3_600_000, start.elapsed + 60_000, 7)))
    }

    @Test fun smallCorrectionsAreIgnored() {
        assertEquals(0L, start.jump(ClockAnchor(start.wall + 61_500, start.elapsed + 60_000, 7)))
    }

    @Test fun aRebootOrAnUnknownAnchorIsNotAJump() {
        assertEquals(0L, start.jump(ClockAnchor(start.wall + 3_600_000, 10_000, 8)))
        assertEquals(0L, ClockAnchor(0, 0, -1).jump(ClockAnchor(start.wall, start.elapsed, -1)))
    }

    /** Keep awake's release is timed on this: real time, so a clock change while the bar is hidden can't move it. */
    @Test fun aDeadlineIsARealTimeAwayWhateverTheClockDid() {
        val deadline = start.wall + 3_600_000
        // Ten minutes later, nothing changed: fifty left.
        assertEquals(3_000_000L, start.left(deadline, ClockAnchor(start.wall + 600_000, start.elapsed + 600_000, 7)))
        // The clock put an hour forward, then back an hour and a half: still fifty left.
        assertEquals(3_000_000L, start.left(deadline, ClockAnchor(start.wall + 600_000 + 3_600_000, start.elapsed + 600_000, 7)))
        assertEquals(3_000_000L, start.left(deadline, ClockAnchor(start.wall + 600_000 - 5_400_000, start.elapsed + 600_000, 7)))
    }

    /** Across a reboot only the wall clock is left to go by. */
    @Test fun aDeadlineAcrossARebootGoesByTheWallClock() {
        val deadline = start.wall + 3_600_000
        assertEquals(1_000_000L, start.left(deadline, ClockAnchor(deadline - 1_000_000, 10_000, 8)))
    }

    /** A deadline set before a reboot follows no clock change after it until it is anchored again in the new boot. */
    @Test fun anAnchorFromAnEarlierBootIsTakenAgainOnceTheNewBootsClockHasSettled() {
        // Up for 30 s: the wall clock may still be the hardware's, which the network then corrects; that is no clock change.
        assertEquals(false, start.stale(ClockAnchor(start.wall + 3_600_000, 30_000, 8)))
        assertEquals(true, start.stale(ClockAnchor(start.wall + 3_600_000, ClockAnchor.SETTLED_MS, 8)))
        // An anchor saved by a version before anchors existed is as good as one from another boot.
        assertEquals(true, ClockAnchor(0, 0, -1).stale(ClockAnchor(start.wall, ClockAnchor.SETTLED_MS, 7)))
        // The same boot, or a boot count that can't be read (no anchor would follow anything): nothing to take again.
        assertEquals(false, start.stale(ClockAnchor(start.wall + 3_600_000, start.elapsed + 3_600_000, 7)))
        assertEquals(false, start.stale(ClockAnchor(start.wall + 3_600_000, ClockAnchor.SETTLED_MS, -1)))
    }
}

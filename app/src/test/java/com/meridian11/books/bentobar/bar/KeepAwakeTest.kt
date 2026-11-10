package com.meridian11.books.bentobar.bar

import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Position.CENTER
import com.meridian11.books.bentobar.data.Position.LEFT
import com.meridian11.books.bentobar.data.Position.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keep awake's choice (`KeepAwakeRule.choose`): which strip's window holds the screen on, whether the others let go, and
 * when a wake lock holds it in their place. The windows are named by their areas here; which area keeps awake is
 * `BarRules.arrange`'s answer (`ArrangeTest`), and `KeepAwake.update` carries the choice out.
 */
class KeepAwakeTest {
    private fun choose(on: Boolean, carrier: Position?, interactive: Boolean = true, locked: Boolean = false) =
        KeepAwakeRule.choose(on, carrier, interactive, locked)

    @Test fun theFlagIsOnTheWindowOfTheAreaThatKeepsAwakeAndEveryOtherLetsGo() {
        for (area in listOf(RIGHT, LEFT, CENTER)) {
            val c = choose(on = true, carrier = area)
            assertEquals(area, c.flag)
            assertTrue("the other windows lose the flag", c.clear)
            assertFalse("a window holds the screen: no wake lock beside it", c.lock)
        }
    }

    /** No strip is on screen with something to draw (the bar is hidden, or every area is empty): today's rule for the one strip. */
    @Test fun withNoStripToCarryTheFlagAWakeLockHoldsTheScreen() {
        val c = choose(on = true, carrier = null)
        assertNull(c.flag)
        assertTrue(c.lock)
    }

    /** The window that comes back still has it, and holds the screen from its first frame. */
    @Test fun withNoStripToCarryItTheFlagsStayWhereTheyAre() {
        assertFalse(choose(on = true, carrier = null).clear)
        assertFalse(choose(on = true, carrier = null, interactive = false).clear)
        assertFalse(choose(on = true, carrier = null, locked = true).clear)
    }

    @Test fun theWakeLockIsNeverHeldWithTheScreenOffOrOnTheLockScreen() {
        assertFalse(choose(on = true, carrier = null, interactive = false).lock)
        assertFalse(choose(on = true, carrier = null, locked = true).lock)
        assertFalse(choose(on = true, carrier = null, interactive = false, locked = true).lock)
    }

    @Test fun withKeepAwakeOffNoWindowHasTheFlagAndNothingIsHeld() {
        for (carrier in listOf(null, RIGHT, LEFT, CENTER)) for (interactive in listOf(true, false)) for (locked in listOf(true, false)) {
            val c = choose(on = false, carrier = carrier, interactive = interactive, locked = locked)
            assertNull("off: no window is given the flag", c.flag)
            assertTrue("off: every window loses it", c.clear)
            assertFalse("off: no wake lock", c.lock)
        }
    }

    /** One window at most: the flag is never asked for on two, whatever the screen and the lock say. */
    @Test fun aStripThatCarriesTheFlagNeedsNoWakeLockWhateverTheScreenSays() {
        for (interactive in listOf(true, false)) for (locked in listOf(true, false)) {
            val c = choose(on = true, carrier = LEFT, interactive = interactive, locked = locked)
            assertEquals(LEFT, c.flag)
            assertTrue(c.clear)
            assertFalse(c.lock)
        }
    }
}

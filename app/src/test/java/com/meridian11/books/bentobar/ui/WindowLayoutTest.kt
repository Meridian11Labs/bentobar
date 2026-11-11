package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.BarStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** The settings window's one model: three layouts by width, one panel at most, and what the header's status line says. */
class WindowLayoutTest {
    @Test fun below760TheHeaderTakesTwoRowsAndThereIsNoPanel() {
        val l = windowLayout(700)
        assertEquals(WindowClass.NARROW, l.kind)
        assertEquals(0, l.panelDp)
        assertEquals(16, l.marginDp)
        assertEquals(SwitchPlace.UNDER_PREVIEW, l.switchAt)
        assertEquals(3, l.switchParts)
    }

    @Test fun from760To1099OnePaneAtFullWidth() {
        for (w in listOf(760, 960, 1099)) {
            val l = windowLayout(w)
            assertEquals("at $w", WindowClass.MID, l.kind)
            assertEquals("at $w", 0, l.panelDp)
            assertEquals("at $w", 24, l.marginDp)
            assertEquals("at $w", SwitchPlace.UNDER_PREVIEW, l.switchAt)
            assertEquals("at $w", 3, l.switchParts)
        }
    }

    @Test fun from1100APaneAndOnePanel348Wide() {
        for (w in listOf(1100, 1499)) {
            val l = windowLayout(w)
            assertEquals("at $w", WindowClass.WIDE, l.kind)
            assertEquals("at $w", 348, l.panelDp)
            assertEquals("at $w", 24, l.marginDp)
            assertEquals("at $w", SwitchPlace.PANEL_HEAD, l.switchAt)
            assertEquals("at $w", 2, l.switchParts)
        }
    }

    @Test fun from1500ThePanelIs420WideAndStillTheOnlyOne() {
        for (w in listOf(1500, 1600, 2400)) {
            val l = windowLayout(w)
            assertEquals("at $w", WindowClass.WIDE, l.kind)
            assertEquals("at $w", 420, l.panelDp)
            assertEquals("at $w", SwitchPlace.PANEL_HEAD, l.switchAt)
        }
    }

    @Test fun theStatusLineSaysWhatTheBarIsDoing() {
        fun line(running: Boolean = true, enabled: Boolean = true, status: BarStatus = BarStatus.SHOWN) = statusLine(running, enabled, status)
        // Off: the one step left, which wins over everything the bar last said.
        assertEquals(R.string.bar_banner_title, line(running = false))
        assertEquals(R.string.bar_banner_title, line(running = false, enabled = false, status = BarStatus.NO_ROOM))
        assertEquals(R.string.status_hidden, line(enabled = false))
        assertEquals(R.string.status_hidden, line(enabled = false, status = BarStatus.NO_ROOM))
        assertEquals(R.string.status_starting, line(status = BarStatus.STOPPED))
        assertEquals(R.string.status_no_room, line(status = BarStatus.NO_ROOM))
        assertEquals(R.string.status_empty, line(status = BarStatus.EMPTY))
        assertEquals(R.string.status_no_bar, line(status = BarStatus.NO_BAR))
        assertEquals(R.string.status_covered, line(status = BarStatus.COVERED))
        assertEquals(R.string.status_live, line(status = BarStatus.SHOWN))
        assertEquals(R.string.status_live, line(status = BarStatus.HIDDEN_BY_USER))
        assertEquals(R.string.status_live, line(status = BarStatus.ASLEEP))
    }

    @Test fun theDotAgreesWithTheLine() {
        assertEquals(StatusTone.STEP, statusTone(running = false, enabled = true, status = BarStatus.SHOWN))
        assertEquals(StatusTone.LIVE, statusTone(running = true, enabled = true, status = BarStatus.SHOWN))
        assertEquals(StatusTone.WAITING, statusTone(running = true, enabled = false, status = BarStatus.SHOWN))
        for (s in listOf(BarStatus.STOPPED, BarStatus.NO_ROOM, BarStatus.EMPTY, BarStatus.NO_BAR, BarStatus.COVERED))
            assertEquals("$s", StatusTone.WAITING, statusTone(running = true, enabled = true, status = s))
    }

    @Test fun theTabsAreBarLookSetupAndAbout() {
        // Adding is the Bar page's library: there is no page for it, and so no tab.
        assertEquals(listOf(Page.BAR, Page.LOOK, Page.SETUP, Page.ABOUT), tabs)
        assertEquals("every page has its tab", Page.entries.toSet(), tabs.toSet())
    }
}

package com.meridian11.books.bentobar.bar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What only reading the code can show: the order in which the strip goes away is load-bearing (AGENTS.md), and what
 * the strip's window took with it is let go of on the way out. The controller is an accessibility service's, with no
 * way to run here.
 */
class HidePathTest {
    private val code = File("src/main/java/com/meridian11/books/bentobar/bar/BarService.kt").readText()
    private val colors = File("src/main/java/com/meridian11/books/bentobar/bar/ColorReader.kt").readText()
    private val menus = File("src/main/java/com/meridian11/books/bentobar/bar/MenuCoordinator.kt").readText()

    /** The one way off the screen: what check() runs when the strip is not to be shown, and what stop() runs. */
    private val hide = code.substringAfter("private fun leaveScreen() {").substringBefore("\n    }\n")
    private val stop = code.substringAfter("fun stop() {").substringBefore("The strip leaves the screen, whatever the reason")

    @Test fun hidingAndStoppingShareTheOneWayOffTheScreen() {
        val hideBranch = code.substringAfter("\"bar hidden (statusBar=").substringBefore("/** adb `debug trace on|off`")
        assertTrue("the hide path runs leaveScreen()", Regex("""^\s+leaveScreen\(\)$""", RegexOption.MULTILINE).containsMatchIn(hideBranch))
        assertTrue("stop() runs leaveScreen()", Regex("""^\s+leaveScreen\(\)$""", RegexOption.MULTILINE).containsMatchIn(stop))
        // Nothing of the hide path is spelled out again beside it.
        for (step in listOf("menus.closeMenu(now = true)", "cancelDrag()", "strip.hide()", "colors.stripLeft()")) assertFalse(step, step in stop.replace("leaveScreen()", ""))
    }

    @Test fun theBarHidesInItsOneOrderAndLetsGoOfThePointerBeforeTheWindowGoes() {
        var at = 0
        for (step in listOf("closeMenu(now = true)", "hideTip()", "cancelDrag()", "hovering = false", "main.removeCallbacks(expandOnHover)",
            "strip.hide()", "colors.stripLeft()", "awake.update()", "Ticker.stop(\"bar\")")) {
            val i = hide.indexOf(step, at)
            assertTrue("$step is missing, or out of order, in the hide path", i >= 0)
            at = i + step.length
        }
        // The readings still to come go with the strip: what stripLeft() does, in the colour reader.
        val left = colors.substringAfter("fun stripLeft()").substringBefore("\n")
        assertTrue(left, "watch.clear()" in left && "main.removeCallbacks(sample)" in left)
    }

    /** The window decision is pinned in BarRulesTest and the schedule in ColorWatchTest; these are the lines that make them do anything. */
    @Test fun theBarChangingLateAsksForAReadingWhereTheDecisionSaysSo() {
        val place = code.substringAfter("private fun place(").substringBefore("\n    }\n")
        val show = place.indexOf("strip.show")
        assertTrue("place() raises SLOW once the strip is back", show >= 0 && place.indexOf("colors.readSoon(ColorWatch.Cause.SLOW, 250)", show) > show)
        val check = code.substringAfter("private fun check(full: Boolean) {").substringBefore("private fun place(")
        assertTrue("a new status bar window asks for a quick reading", "if (d.read == BarRules.Read.NEW_BAR_WINDOW) colors.readSoon(ColorWatch.Cause.QUICK, 250)" in check)
        assertTrue("windows that moved are told to the colour reader", "else if (d.read == BarRules.Read.WINDOWS_MOVED) colors.windowsMoved()" in check)
    }

    /** A screenshot's answer can come after stop(): running it is what closes its buffer, so the thread it runs on stays. */
    @Test fun aScreenshotAnsweredAfterStopStillClosesItsBuffer() {
        val scan = File("src/main/java/com/meridian11/books/bentobar/bar/StatusBarScan.kt").readText()
        assertFalse("the answers' thread is never shut down", ".shutdown()" in code || ".shutdown()" in scan)
        assertFalse("no thread of the controller's own", "newSingleThreadExecutor" in code)
        assertTrue("one thread for the whole process", Regex("""private val \w+ = Executors\.newSingleThreadExecutor\(\)""").containsMatchIn(scan))
    }

    @Test fun whatTheHoverHeldOutFoldsAwayButWhatWasPinnedStays() {
        assertTrue(hide, Regex("""if \(!pinned && expanded\.value\) expanded\.value = false""").containsMatchIn(hide))
    }

    /** Hidden items folded by anything but the hide path stay folded: the pin goes with them, or the next start of the service opens them with no click. */
    @Test fun whatFoldsHiddenItemsAlsoClearsTheirPin() {
        val fold = code.substringAfter("private fun fold() {").substringBefore("\n    }\n")
        assertTrue(fold, "expanded.value = false" in fold && "pinned = false" in fold && "pinnedOpen = false" in fold)
        assertEquals("outside the fold, only the hide path turns them off (and there nothing was pinned)", 2,
            Regex("""expanded\.value = false""").findAll(code).count())
        val wheel = code.substringAfter("override fun wheel(up: Boolean) {").substringBefore("\n        }\n")
        assertTrue(wheel, "fold()" in wheel)
    }

    @Test fun stoppingLeavesNothingRevealedInTheProcesssTicker() {
        assertTrue(stop, "Ticker.revealHidden = false" in stop)
        // After closeMenu (in the way off the screen), which sets it from `expanded` when a popup was open.
        assertTrue(hide, "closeMenu(now = true)" in hide)
        assertTrue(stop.indexOf("leaveScreen()") < stop.indexOf("Ticker.revealHidden = false"))
    }

    @Test fun theDisplaysItemFollowsTheServiceSoKeepAwakeWhileConnectedHolds() {
        val onConfig = code.substringAfter("private fun onConfig() {").substringBefore("\n    }\n")
        assertTrue("onConfig must call DisplaysItem.follow(true): only it sets the rule's `ruled`", "DisplaysItem.follow(true)" in onConfig)
        val off = stop.indexOf("DisplaysItem.follow(false)")
        assertTrue("stop() must call DisplaysItem.follow(false)", off >= 0)
        assertTrue("follow(false) comes before Ticker.stop, in the way off the screen", off < stop.indexOf("leaveScreen()") && "Ticker.stop(\"bar\")" in hide)
    }

    /** A measure posted before the bar left arrives after it: it finds a bar that is gone, and places nothing. */
    @Test fun aMeasureThatComesLateNeverPutsBackABarThatHasLeft() {
        val gone = hide.indexOf("showing = false")
        assertTrue(hide, gone >= 0 && hide.indexOf("strip.hide()", gone) > gone)
        val apply = code.substringAfter("private fun applyWidth(").substringBefore("private var dragFrom")
        assertTrue(apply, "if (!strip.shown) return" in apply)
        assertTrue(apply, "if (showing) lastPlace?.let { (s, screenW) -> place(s, screenW) }" in apply)
        assertEquals("the strip is placed nowhere else in it: $apply", 1, Regex("""\bplace\(""").findAll(apply).count())
    }

    /** A window that has not been measured has nothing to say about what it draws: it is not on show until it has. */
    @Test fun aStripsWindowStaysInvisibleFromItsShowUntilItsFirstMeasure() {
        val strip = File("src/main/java/com/meridian11/books/bentobar/bar/AreaStrip.kt").readText()
        val show = strip.substringAfter("fun show(").substringBefore("\n    }\n")
        val shown = show.indexOf("window.show(content)")
        assertTrue(show, shown >= 0 && show.indexOf("window.setContentVisible(false)", shown) > shown)
        assertTrue(show, "pending = true" in show)
        val apply = code.substringAfter("private fun applyWidth(").substringBefore("private var dragFrom")
        val measured = apply.indexOf("strip.pending = false")
        assertTrue(apply, measured >= 0 && apply.indexOf("strip.setContentVisible(!empty)", measured) > measured)
    }

    /**
     * What the bar does once when it is back (keep awake, the reading it owes, the ticker) is done after every strip has
     * its window, and after the look at whether Android gave it one: not once per strip, and never for a refused window.
     */
    @Test fun whatTheBarDoesOnceWhenItIsBackComesAfterEveryStripHasItsWindow() {
        val place = code.substringAfter("private fun place(").substringBefore("\n    }\n")
        val loop = place.substringAfter("for (spot in a.spots) {").substringBefore("\n        }\n")
        assertTrue(loop, "strip.show" in loop)
        for (once in listOf("awake.update()", "colors.readSoon(", "Ticker.start(", "BarStatus.STOPPED")) assertFalse("$once in $loop", once in loop)
        val after = place.substringAfter(loop)
        val order = listOf("if (strips.none { it.shown }) { BarStatus.current.value = BarStatus.STOPPED; return }", "awake.update()",
            "if (a.first) {", "colors.readSoon(ColorWatch.Cause.SLOW, 250)", "Ticker.start(\"bar\")").map { after.indexOf(it) }
        assertTrue("$order in $after", order.all { it >= 0 } && order == order.sorted())
    }

    /** One strip going while the bar stays: what hangs from it goes before its window, and the bar's own ending is not run. */
    @Test fun aStripThatLeavesAloneTakesItsPopupsFirstAndDoesNotEndTheBar() {
        val leave = code.substringAfter("private fun leave(strip: AreaStrip) {").substringBefore("\n    }\n")
        val order = listOf("menus.closeFor(strip)", "cancelDrag()", "strip.hide()").map { leave.indexOf(it) }
        assertTrue("$order in $leave", order.all { it >= 0 } && order == order.sorted())
        for (bars in listOf("Ticker.stop", "colors.stripLeft()", "expanded.value = false")) assertFalse("$bars in $leave", bars in leave)
        assertTrue(hide, "for (strip in strips) strip.hide()" in hide)
    }

    /**
     * With no window carrying the flag for a moment, the screen could go off in it. Which window and when the wake lock
     * holds instead is `KeepAwakeRule.choose` (KeepAwakeTest); what only the code shows is that update() does as it says,
     * in that order.
     */
    @Test fun keepAwakesFlagIsOnTheStripThatCarriesItBeforeItLeavesAnyOther() {
        val awake = File("src/main/java/com/meridian11/books/bentobar/bar/KeepAwake.kt").readText()
        val update = awake.substringAfter("fun update() {").substringBefore("\n    }\n")
        val asked = update.indexOf("KeepAwakeRule.choose(on, if (on) carrier() else null, pm.isInteractive, km.isKeyguardLocked)")
        assertTrue("update() asks the one rule: $update", asked >= 0)
        val set = update.indexOf("flag(it, true)", asked)
        assertTrue(update, set >= 0 && update.indexOf("flag(window, false)", set) > set)
        assertTrue("the wake lock is the rule's too: $update", "if (c.lock && !screenLock.isHeld)" in update && "else if (!c.lock && screenLock.isHeld) screenLock.release()" in update)
        for (own in listOf("&& pm.isInteractive", "!km.isKeyguardLocked")) assertFalse("update() decides nothing itself: $own", own in update)
    }

    @Test fun anItemsMenuIsToldItIsOpeningBeforeItsFirstFrame() {
        val toggle = menus.substringAfter("fun toggleMenu(").substringBefore("val loc = strip.locationOnScreen()")
        val close = toggle.indexOf("closeMenu(dissolve = true)")
        assertTrue("toggleMenu closes the old popup first", close >= 0)
        assertTrue("menuOpening runs after closeMenu(dissolve = true)", toggle.indexOf("menuOpening", close) > close)
    }

    /** A tooltip ends with its item: the LaunchedEffect that says "the hover ended" is cancelled, not run, when the item leaves the strip. */
    @Test fun aTooltipEndsWithItsItem() {
        val ui = File("src/main/java/com/meridian11/books/bentobar/bar/BarUi.kt").readText()
        assertTrue(ui, Regex("""onDispose \{ if \(hovered\) events\.itemHover\(entry\.item, bounds, false\) \}""").containsMatchIn(ui))
        val hover = code.substringAfter("override fun hover(inside: Boolean) {").substringBefore("\n        }\n")
        assertTrue("the pointer leaving the strip ends its tooltip: $hover", Regex("""if \(!inside\) menus\.hideTip\(\)""").containsMatchIn(hover))
    }

    @Test fun itemsThatLeftOrDidNotFitLeaveNoBoxBehind() {
        val ui = File("src/main/java/com/meridian11/books/bentobar/bar/BarUi.kt").readText()
        assertTrue("items and the chevron report going", ui.contains("onDispose { glow.gone(entry.item.id); events.gone(entry.item.id) }") &&
            ui.contains("onDispose { glow.gone(\"chevron\"); events.gone(\"chevron\") }"))
        assertTrue("items FitRow left out report going", Regex("""glow\.dropped\(ids\).*events::gone""").containsMatchIn(ui))
        assertTrue(Regex("""override fun gone\(id: String\) \{ placed -= id \}""").containsMatchIn(code))
        assertFalse("outside() is the same rule as the tested one", "StripHighlight.target(x, placed" in menus)
    }
}

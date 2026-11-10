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

    /** The pointer is on one strip at a time: another strip going must not end a hover, or a drag, that is not in it. */
    @Test fun aStripThatLeavesEndsOnlyWhatThePointerHeldInIt() {
        val leave = code.substringAfter("private fun leave(strip: AreaStrip) {").substringBefore("\n    }\n")
        assertTrue(leave, "if (dragFrom?.first in strip.placed) cancelDrag()" in leave)
        assertTrue(leave, "if (hovering && hoverStrip === strip) events.getValue(strip).hover(false)" in leave)
        val hover = code.substringAfter("override fun hover(inside: Boolean) {").substringBefore("\n        }\n")
        assertTrue("a hover says which strip it is on: $hover", "hovering = inside; hoverStrip = if (inside) strip else null" in hover)
        assertTrue("the bar leaving ends it whichever strip it was on: $hide", "hovering = false; hoverStrip = null" in hide)
    }

    /** The areas' windows: one for each area in use that has room, each planned, measured and drawn by its own area. */
    @Test fun everyStripIsItsOwnAreasAndNothingInTheBarAsksWherePositionSays() {
        val place = code.substringAfter("private fun place(").substringBefore("\n    }\n")
        assertTrue(place, "home = cfg.homeArea(), inUse = cfg.areasInUse()," in place)
        val host = code.substringAfter("private fun StripHost(").substringBefore("private var stripEmpty")
        for (own in listOf(".stripPlan(strip.area, ", "home = strip.area == cfg.homeArea())", "bias = when (strip.area) {", "chevronOnLeft = strip.area != Position.LEFT,"))
            assertTrue("$own in $host", own in host)
        val menu = code.substringAfter("fun openBarMenu(): Boolean {").substringBefore("\n    }\n")
        assertTrue("the keyboard's menu hangs from the carrier, at the end its area says: $menu", "val strip = carrierStrip()" in menu && "if (strip.area == Position.LEFT)" in menu)
        // The old one setting moves nothing in the bar: only the migration of an old layout reads it (data/).
        for (file in File("src/main/java/com/meridian11/books/bentobar/bar").listFiles()!!.filter { it.extension == "kt" })
            assertFalse("${file.name} reads Position's old setting", Regex("""\b(cfg|value|config)\.position\b""").containsMatchIn(file.readText()))
        val preview = File("src/main/java/com/meridian11/books/bentobar/ui/BarPage.kt").readText()
        assertTrue("the preview's ‹ stands where the bar's does", "chevronOnLeft = cfg.homeArea() != Position.LEFT," in preview)
    }

    /**
     * What a strip has no room for is its own: its ‹ is for its own items, and a strip with room says nothing about
     * another's. Settings and the ‹ menu get every strip's as one set, from one place.
     */
    @Test fun whatAStripHasNoRoomForIsItsOwnAndIsPublishedAsOneSet() {
        val host = code.substringAfter("private fun StripHost(").substringBefore("private var stripEmpty")
        assertTrue(host, "expanded.value, strip.dropped.isNotEmpty())" in host)
        assertTrue(host, "onOverflow = { ids -> main.post { if (strip.shown) { strip.dropped = ids; publishOverflow() } } }," in host)
        assertFalse("no strip plans by what another has no room for: $host", "BarOverflow" in host)
        val publish = code.substringAfter("private fun publishOverflow() {").substringBefore("\n    }\n")
        assertTrue(publish, "strips.flatMapTo(mutableSetOf()) { it.dropped }" in publish)
        assertTrue("the bar is read again only when the set changed: $publish", "if (BarOverflow.ids.value != ids) { BarOverflow.ids.value = ids; main.post(scanNow) }" in publish)
        assertEquals("written by stop() and by publishOverflow() alone", 2, Regex("""BarOverflow\.ids\.value = """).findAll(code).count())
        // A strip that left has nothing left over: its items are no longer said not to fit (task 6 says they are owed).
        val leave = code.substringAfter("private fun leave(strip: AreaStrip) {").substringBefore("\n    }\n")
        val gone = leave.indexOf("strip.hide()")
        assertTrue(leave, gone >= 0 && leave.indexOf("publishOverflow()", gone) > gone)
    }

    /**
     * The first strip's ‹ comes and goes for the areas with no room, so nothing behind it may follow that ‹, or an area
     * would get room because it had none and lose it because it had some. With several areas in use the first strip keeps
     * a ‹'s room in its own fit whatever it draws, and says its reach: its width with that room, the same number with the
     * ‹ and without. The others are placed by it ([BarRules.arrange]). With one area the fit is the strip's own, as it was.
     */
    @Test fun theFirstStripOfSeveralKeepsAChevronsRoomAndTheOthersStandBehindItsReach() {
        val host = code.substringAfter("private fun StripHost(").substringBefore("private var stripEmpty")
        assertTrue(host, "chevronAlways = plan.chevronAlways || (strip.area == carrier && cfg.areasInUse().size > 1)," in host)
        assertTrue("a strip that becomes the first is composed again", "private var carrier by mutableStateOf(ROOM_ORDER.first())" in code)
        // The room it keeps, the room in its reach and the room `arrange` asks for are one number.
        assertTrue(host, "chevronReservePx = (30 * density).toInt()," in host)
        val place = code.substringAfter("private fun place(").substringBefore("\n    }\n")
        assertTrue(place, "least = (30 * density).toInt() + 2 * ends," in place)
        val ui = File("src/main/java/com/meridian11/books/bentobar/bar/BarUi.kt").readText()
        assertTrue("the reach is what it keeps, a ‹'s whole room and its two ends", "onFit = { used -> onReach(used + chevronReservePx + 2 * ends) }," in ui)
        // Read where the width is reported, as state: a fit that changed under an unchanged width is still heard.
        assertTrue(host, "val reach = remember { mutableIntStateOf(0) }" in host && "onReach = { reach.intValue = it }," in host)
        assertTrue(host, "val r = reach.intValue" in host && "|| r != strip.reach) main.post { applyWidth(strip, w, empty, r) }" in host)
        val apply = code.substringAfter("private fun applyWidth(").substringBefore("private var dragFrom")
        assertTrue(apply, "strip.reach = reach" in apply)
        val strip = File("src/main/java/com/meridian11/books/bentobar/bar/AreaStrip.kt").readText()
        assertTrue("kept while it has no window, as its width is", "fun state() = BarRules.AreaState(measured, empty, shown, pending, reach)" in strip)
        assertFalse(strip.substringAfter("fun hide() {").substringBefore("}"), "reach" in strip.substringAfter("fun hide() {").substringBefore("}"))
    }

    /**
     * Strips come and go in a placing, so whether the bar draws anything is asked again there. That adds no reading of
     * the bar: the colour reader hears of it only when the answer changed, and a first show has its one slow reading.
     */
    @Test fun stripsComingAndGoingAskAgainWhetherTheBarDrawsAndAddNoReading() {
        val place = code.substringAfter("private fun place(").substringBefore("\n    }\n")
        val after = place.substringAfter(place.substringAfter("for (spot in a.spots) {").substringBefore("\n        }\n"))
        val asked = after.indexOf("stripEmpty = strips.none { it.draws }")
        assertTrue(after, asked >= 0 && asked < after.indexOf("BarStatus.current.value = status()"))
        assertTrue(after, "if (a.first || a.awake != held || stripEmpty != was) awake.update()" in after)
        val first = after.indexOf("if (a.first) {")
        val told = after.indexOf("} else if (stripEmpty != was) {", first)
        assertTrue("only a change, and never at a first show: $after", first >= 0 && told > first && after.indexOf("colors.contentChanged(stripEmpty)", told) > told)
        assertEquals(1, Regex("""colors\.contentChanged\(""").findAll(place).count())
        assertEquals("one reading asked for in a placing, the first show's", 1, Regex("""colors\.readSoon\(""").findAll(place).count())
        assertFalse("a placing takes no reading itself", "sampleNow" in place || "windowsMoved" in place)
    }

    /**
     * Android can refuse one area's window and give the others theirs: the status says so and the next placing tries
     * again. And when the strips that were up have left and their successors were refused, the bar is off the screen:
     * it leaves the one way it always does, so nothing runs behind a bar that is not there.
     */
    @Test fun aRefusedWindowIsSaidAndABarWithNoWindowLeftLeavesTheOneWay() {
        val place = code.substringAfter("private fun place(").substringBefore("\n    }\n")
        val loop = place.substringAfter("for (spot in a.spots) {").substringBefore("\n        }\n")
        val after = place.substringAfter(loop)
        val refused = after.indexOf("refused = a.spots.any { spot -> strips.none { it.area == spot.area && it.shown } }")
        assertTrue(after, refused >= 0 && refused < after.indexOf("BarStatus.current.value = status()"))
        assertTrue("every status the bar gives says it", "private fun status() = if (refused) BarStatus.STOPPED else StripContent.status(" in code)
        val left = after.indexOf("if (!a.first && strips.none { it.shown }) leaveScreen()")
        assertTrue(after, left >= 0 && left < after.indexOf("if (strips.none { it.shown }) { BarStatus.current.value = BarStatus.STOPPED; return }"))
    }

    /**
     * The window that keeps the screen on is about to go while the bar stays: the flag is on the next one, or the wake
     * lock holds, before it does.
     */
    @Test fun keepAwakeIsHandedOnBeforeTheWindowThatHeldItGoes() {
        val place = code.substringAfter("private fun place(").substringBefore("\n    }\n")
        val order = listOf("val held = awakeArea", "awakeArea = a.awake", "if (held != null && held in a.leave) awake.update()",
            "for (strip in strips) if (strip.area in a.leave) leave(strip)").map { place.indexOf(it) }
        assertTrue("$order in $place", order.all { it >= 0 } && order == order.sorted())
        assertTrue("the flag goes only to a strip that draws", "{ strips.firstOrNull { it.area == awakeArea }?.takeIf { it.draws }?.window }" in code)
    }

    /**
     * The adb hooks, with more than one strip up: one that names an item goes to the strip that drew it, and the dump
     * says what each area's window is doing, beside the two words about the whole bar it always had.
     */
    @Test fun aDebugHookThatNamesAnItemGoesToTheStripThatDrewItAndTheDumpSaysEachArea() {
        val debug = code.substringAfter("fun debug(cmd: List<String>): String {")
        val open = debug.substringAfter("\"open\", \"ctx\" -> {").substringBefore("\n            }\n")
        assertTrue(open, "val drew = strips.firstOrNull { item.id in it.placed } ?: return \"item not drawn yet\"" in open)
        assertTrue("its press is told to that strip's events: $open", "this.events.getValue(drew)" in open)
        val dump = debug.substringAfter("\"dump\" -> ").substringBefore("\"open\", \"ctx\"")
        for (word in listOf("shown=\${strips.any { it.shown }}", "empty=\$stripEmpty", "carrier=\${word(carrier)}", "owed=\${owed.value.map { word(it) }}",
            "strips.joinToString(\" \")", "shown=\${it.shown}", "empty=\${it.empty}", "pending=\${it.pending}", "width=\${it.params.width}", "x=\${it.params.x}",
            "budget=\${it.maxWidth.intValue}", "placed=\${it.placed}"))
            assertTrue("$word in $dump", word in dump)
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

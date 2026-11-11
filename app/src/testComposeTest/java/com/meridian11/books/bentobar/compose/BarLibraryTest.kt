package com.meridian11.books.bentobar.compose

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Display
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.data.removed
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.ItemGroup
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.ui.BarPage
import com.meridian11.books.bentobar.ui.LocalWindowLayout
import com.meridian11.books.bentobar.ui.SettingsTheme
import com.meridian11.books.bentobar.ui.Undo
import com.meridian11.books.bentobar.ui.windowLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The library, the panel's "Add items": a type's + and what its row says after, the setups remembered under their type
 * (a line each, with Forget and a + of its own), the filter and its keys, and where Remove from the bar leaves the panel.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1200dp-h800dp-mdpi")
class BarLibraryTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var before: BarConfig
    private lateinit var inputMode: InputModeManager
    private var selected by mutableStateOf<String?>(null)

    @Before fun env() { Env.init(app); before = Store.config.value; Undo.last.value = null }
    @After fun forget() { Store.update { before }; Undo.last.value = null }

    private fun page(select: String?, vararg items: ItemConfig, mode: HiddenMode = HiddenMode.SHOW_ALL, width: Int = 1180): BarConfig {
        val layout = BarConfig(items = items.toList(), position = Position.RIGHT, hiddenMode = mode)
        Store.update { layout }
        selected = select
        rule.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalWindowLayout provides windowLayout(width)) {
                SettingsTheme(dark = false) { BarPage(selected, onSelect = { selected = it }) }
            }
        }
        rule.waitForIdle()
        return layout
    }

    /** As someone working by keyboard has it: in touch mode nothing that takes a click takes the focus. */
    private fun keyboard() = rule.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }

    private fun item(id: String, type: String, area: Position = Position.RIGHT) = ItemConfig(id, type, area = area)
    private fun fresh(id: String, type: String) = Items.of(type)!!.fresh(id, Position.RIGHT)
    private fun clockIn(id: String, zone: String, section: Section = Section.SHOWN, area: Position = Position.RIGHT) =
        fresh(id, "clock").with("zone", zone).copy(section = section, area = area)
    private val bar = arrayOf(item("a", "memory"), item("b", "battery"))

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private fun count(shown: Int, all: Int) = app.resources.getQuantityString(R.plurals.add_filter_count, shown, shown, all)
    private fun title(type: String) = str(Items.of(type)!!.titleRes)
    private fun now(id: String) = Store.config.value.items.firstOrNull { it.id == id }
    private fun ids() = Store.config.value.items.map { it.id }
    private fun tag(tag: String) = rule.onNodeWithTag(tag)
    private fun within(tag: String) = hasAnyAncestor(hasTestTag(tag))
    private val lines get() = rule.onAllNodes(SemanticsMatcher("a remembered setup's line") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("line:") == true
    })
    /** Where a row stands in the window, in or out of sight. */
    private fun top(tag: String) = tag(tag).fetchSemanticsNode().positionInRoot.y
    private fun bottom(tag: String) = tag(tag).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }
    private fun filterText() = tag("filter").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun switchPart(text: Int) = rule.onNode(hasText(str(text)) and within("panel-switch"))
    private fun remove() { rule.onNode(hasText(str(R.string.detail_remove)) and hasClickAction()).performClick(); rule.waitForIdle() }
    /** A click on something in the library, scrolled to first: a click on what is out of sight lands nowhere. */
    private fun click(tag: String) { tag(tag).performScrollTo(); rule.waitForIdle(); tag(tag).performClick(); rule.waitForIdle() }
    private fun press(key: Key, on: String = "bar-list") { tag(on).performKeyInput { pressKey(key) }; rule.waitForIdle() }
    private fun press(with: Key, key: Key, on: String = "bar-list") { tag(on).performKeyInput { withKeyDown(with) { pressKey(key) } }; rule.waitForIdle() }
    private val rows get() = ItemGroup.entries.sumOf { Items.inGroup(it).size }

    @Test fun removingASetUpItemPutsOneLineUnderItsTypeWithItsCityAsItsWords() {
        page("k", *bar, clockIn("k", "Asia/Tokyo"))
        remove()
        assertEquals(Section.OFF, now("k")?.section)
        tag("library").assertExists()
        lines.assertCountEquals(1)
        rule.onNode(hasText("Tokyo") and within("line:k")).assertExists()
        assertEquals("the line stands right under its type", bottom("type:clock"), top("line:k"), 1f)
        // And the bar's slip names it as the library does.
        rule.onNode(hasText(str(R.string.bar_removed, str(R.string.library_setup, title("clock"), "Tokyo"))) and within("bar-list")).assertExists()
    }

    @Test fun removingAnItemAsThePlusMadeItPutsNoLine() {
        page("f", *bar, fresh("f", "clock"))
        remove()
        assertNull(now("f"))
        tag("library").assertExists()
        lines.assertCountEquals(0)
        rule.onNode(hasText(str(R.string.bar_removed, title("clock"))) and within("bar-list")).assertExists()
    }

    @Test fun plusOnTheLineBringsTheItemBackWithItsOptionsIntoTheAreaInUseAndTheLineIsGone() {
        val tokyo = clockIn("k", "Asia/Tokyo", Section.OFF, Position.RIGHT)
        val layout = page(null, item("a", "memory", Position.LEFT), tokyo)
        tag("line:k").assertExists()
        tag("row:k").assertDoesNotExist()

        click("add-line:k")
        val back = now("k")!!
        assertEquals(Section.SHOWN, back.section)
        assertEquals("the area in use, not the one it was stored with", Position.LEFT, back.area)
        assertEquals(tokyo.options, back.options)
        lines.assertCountEquals(0)
        tag("row:k").assertExists()
        rule.onNodeWithTag("added-mark", useUnmergedTree = true).assertExists()
        // Ctrl+Z is the same step back.
        keyboard()
        tag("row:a").requestFocus()
        press(Key.CtrlLeft, Key.Z)
        assertEquals(layout, Store.config.value)
        tag("line:k").assertExists()
    }

    @Test fun plusOnTheTypeWhileALineExistsAddsAFreshItemAndKeepsTheLine() {
        val tokyo = clockIn("k", "Asia/Tokyo", Section.OFF)
        page(null, *bar, tokyo)
        click("add:clock")
        assertEquals(tokyo, now("k"))
        tag("line:k").assertExists()
        val new = Store.config.value.items.single { it.type == "clock" && it.id != "k" }
        assertEquals(fresh(new.id, "clock"), new)
        // The type's row says so, with the Undo; the bar's new row is marked.
        rule.onNode(hasText(str(R.string.add_added)) and within("type:clock")).assertExists()
        rule.onNode(hasText(str(R.string.add_added)) and within("row:${new.id}"), useUnmergedTree = true).assertExists()
        click("library-undo")
        assertNull(now(new.id))
        tag("line:k").assertExists()
        tag("added").assertDoesNotExist()
    }

    @Test fun undoOfAnAddedItemThatWasSetUpSinceLeavesItAsALineAndNothingSaysAddedAnyMore() {
        val layout = page(null, *bar)
        click("add:clock")
        val new = Store.config.value.items.single { it.type == "clock" }
        // Set up in its options, which changes no order: the Undo is still offered.
        Store.updateItem(new.id) { it.with("zone", "Asia/Tokyo") }
        rule.waitForIdle()
        rule.onNode(hasText(str(R.string.add_added)) and within("type:clock")).assertExists()
        click("library-undo")
        // Out of the bar, and not lost: a line under its type, with its city.
        assertEquals(layout.items, Store.config.value.items.filter { it.section != Section.OFF })
        assertEquals(Section.OFF, now(new.id)?.section)
        assertEquals("Asia/Tokyo", now(new.id)?.options?.get("zone"))
        tag("row:${new.id}").assertDoesNotExist()
        rule.onNode(hasText("Tokyo") and within("line:${new.id}")).assertExists()
        tag("added").assertDoesNotExist()
        tag("library-undo").assertDoesNotExist()
        rule.onNode(hasText(str(R.string.add_added))).assertDoesNotExist()
    }

    @Test fun forgetRemovesTheLineAndUndoBringsItBack() {
        val layout = page(null, *bar, clockIn("k", "Asia/Tokyo", Section.OFF), clockIn("l", "Europe/London", Section.OFF))
        click("forget:k")
        assertEquals(listOf("a", "b", "l"), ids())
        tag("line:k").assertDoesNotExist()
        // Its place says what went, between its type and the next line, until the next change.
        rule.onNode(hasText(str(R.string.library_forgot, str(R.string.library_setup, title("clock"), "Tokyo"))) and within("forgot")).assertExists()
        assertEquals(bottom("type:clock"), top("forgot"), 1f)
        assertEquals(bottom("forgot"), top("line:l"), 1f)

        click("library-undo")
        assertEquals(layout, Store.config.value)
        tag("forgot").assertDoesNotExist()
        assertEquals(bottom("type:clock"), top("line:k"), 1f)
    }

    @Test fun aForgottenLinesPlaceClosesWithTheNextChange() {
        page(null, *bar, clockIn("k", "Asia/Tokyo", Section.OFF))
        click("forget:k")
        tag("forgot").assertExists()
        click("add:memory")
        tag("forgot").assertDoesNotExist()
        lines.assertCountEquals(0)
    }

    @Test fun severalSetupsOfOneTypeAreSeveralLinesInTheirOrder() {
        page(null, *bar, clockIn("k", "Asia/Tokyo", Section.OFF), fresh("t", "timer").with("label", "Tea").copy(section = Section.OFF),
            clockIn("l", "Europe/London", Section.OFF))
        lines.assertCountEquals(3)
        rule.onNode(hasText("Tokyo") and within("line:k")).assertExists()
        rule.onNode(hasText("London") and within("line:l")).assertExists()
        rule.onNode(hasText("Tea") and within("line:t")).assertExists()
        assertEquals(bottom("type:clock"), top("line:k"), 1f)
        assertEquals(bottom("line:k"), top("line:l"), 1f)
        assertEquals(bottom("type:timer"), top("line:t"), 1f)
    }

    @Test fun aLineIsReadByItsTypeAndItsWordsWithTheActionsAddAndForget() {
        page(null, *bar, clockIn("l", "Europe/London", Section.OFF))
        val words = rule.onNode(hasText("London") and within("line:l"))
        words.assertContentDescriptionEquals("${title("clock")}, London")
        val actions = words.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(str(R.string.add_add), str(R.string.library_forget)), actions.map { it.label })
        tag("add-line:l").assertContentDescriptionEquals(str(R.string.library_add_setup, title("clock"), "London"))
        tag("forget:l").assertContentDescriptionEquals(str(R.string.library_forget_setup, title("clock"), "London"))
        tag("add:clock").assertContentDescriptionEquals(str(R.string.library_add, title("clock")))
        rule.runOnIdle { actions.first { it.label == str(R.string.library_forget) }.action() }
        rule.waitForIdle()
        assertNull(now("l"))
    }

    @Test fun theFilterMatchesANameADescriptionAndALinesWordsAndCountsWhatItShows() {
        val lisbon = fresh("w", "weather").with("city", "Lisbon").with("lat", "38.7").with("lon", "-9.1").copy(section = Section.OFF)
        page(null, *bar, lisbon, clockIn("k", "Asia/Tokyo", Section.OFF))
        val all = rows + 2
        tag("filter-count").assertDoesNotExist()

        // A name: the type with all its lines.
        tag("filter").performTextInput(title("clock").take(4))
        rule.waitForIdle()
        tag("type:clock").assertExists()
        tag("line:k").assertExists()
        tag("type:memory").assertDoesNotExist()
        val shown = ItemGroup.entries.flatMap { Items.inGroup(it) }.count { str(it.titleRes).contains(title("clock").take(4), true) || str(it.blurbRes).contains(title("clock").take(4), true) } + 1
        tag("filter-count").assertTextEquals(count(shown, all))

        // Esc empties it, and everything is back.
        press(Key.Escape, on = "filter")
        assertEquals("", filterText())
        tag("filter-count").assertDoesNotExist()
        tag("type:memory").assertExists()

        // A description.
        val blurb = str(Items.of("storage")!!.blurbRes)
        assertFalse(title("storage").contains(blurb, true))
        tag("filter").performTextInput(blurb.uppercase())
        rule.waitForIdle()
        tag("type:storage").assertExists()
        tag("type:clock").assertDoesNotExist()
        tag("filter-count").assertTextEquals(count(1, all))
        press(Key.Escape, on = "filter")

        // A line's words: its type, with that line only.
        tag("filter").performTextInput("lisb")
        rule.waitForIdle()
        tag("type:weather").assertExists()
        tag("line:w").assertExists()
        tag("type:clock").assertDoesNotExist()
        tag("line:k").assertDoesNotExist()
        tag("filter-count").assertTextEquals(count(2, all))
        assertEquals("3 of 32 shown.", count(3, 32))
    }

    @Test fun ctrlFTurnsThePanelToTheLibraryWithTheKeyboardInTheFilterAndKeepsTheSelection() {
        val layout = page("a", *bar)
        keyboard()
        tag("row:a").requestFocus()
        tag("options").assertExists()
        press(Key.CtrlLeft, Key.F)
        tag("library").assertExists()
        tag("options").assertDoesNotExist()
        tag("filter").assertIsFocused()
        assertEquals("a", selected)
        tag("row:a").assertIsSelected()
        assertEquals(layout, Store.config.value)
        assertEquals("the key is not typed into it", "", filterText())
    }

    /** How many caps with this word the filter's field shows. */
    private fun caps(word: Int) = rule.onAllNodes(hasText(str(word)) and within("filter"), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun theFilterShowsEscOnlyWhileEscHasSomethingToDo() {
        page("a", *bar)
        keyboard()
        tag("row:a").requestFocus()
        press(Key.CtrlLeft, Key.F)
        assertEquals("the field is empty, but the item can be let go from it", 1, caps(R.string.key_esc))
        tag("filter").performTextInput("mem")
        rule.waitForIdle()
        assertEquals(1, caps(R.string.key_esc))
        press(Key.Escape, on = "filter")
        assertEquals("the filter is empty again and the item is still held", 1, caps(R.string.key_esc))
        press(Key.Escape, on = "filter")
        assertNull(selected)
        tag("filter").assertIsFocused()
        assertEquals("nothing is left for Esc to do", 0, caps(R.string.key_esc))
        assertEquals("nor would Ctrl+F bring the keyboard, which is here", 0, caps(R.string.key_f))
        tag("filter").performTextInput("m")
        rule.waitForIdle()
        assertEquals("something to empty", 1, caps(R.string.key_esc))
    }

    @Test fun beforeTheFilterHasTheKeyboardItShowsTheKeysThatBringItThere() {
        page(null, *bar)
        assertEquals(1, caps(R.string.key_f))
        assertEquals(0, caps(R.string.key_esc))
    }

    @Test fun escInTheFilterEmptiesItAndThenLetsTheItemGoThatCtrlFKept() {
        val layout = page("a", *bar)
        keyboard()
        tag("row:a").requestFocus()
        press(Key.CtrlLeft, Key.F)
        tag("filter").performTextInput("mem")
        rule.waitForIdle()
        // One thing per press, the nearest first.
        press(Key.Escape, on = "filter")
        assertEquals("", filterText())
        assertEquals("a", selected)
        press(Key.Escape, on = "filter")
        assertNull("the item is let go from the filter too", selected)
        tag("library").assertExists()
        assertEquals(layout, Store.config.value)
    }

    @Test fun belowTheWidthWithRoomForBothCtrlFTurnsToAddItems() {
        page("a", *bar, width = 960)
        keyboard()
        tag("options").assertExists()
        press(Key.CtrlLeft, Key.F, on = "options")
        switchPart(R.string.page_add).assertIsSelected()
        tag("filter").assertIsFocused()
        assertEquals("a", selected)
    }

    @Test fun deleteBackspaceAndCtrlZTypedInTheFilterEditItsTextAndLeaveTheLayoutAlone() {
        page(null, *bar, item("c", "clock"))
        keyboard()
        // Something Ctrl+Z could take back, and a row the keys would act on.
        tag("row:c").requestFocus()
        press(Key.Delete)
        assertEquals(listOf("a", "b"), ids())
        val layout = Store.config.value

        tag("filter").requestFocus()
        tag("filter").performTextInput("mem")
        rule.waitForIdle()
        tag("filter").assertIsFocused()
        press(Key.Backspace, on = "filter")
        assertEquals("me", filterText())
        press(Key.Delete, on = "filter")
        press(Key.CtrlLeft, Key.Z, on = "filter")
        assertEquals("what was removed stays removed, and nothing else went", layout, Store.config.value)
        tag("slip").assertExists()
        tag("filter").assertIsFocused()
    }

    @Test fun aLetterTypedWithNoFieldInFocusStartsTheFilter() {
        val layout = page(null, *bar)
        keyboard()
        tag("row:a").requestFocus()
        press(Key.C)
        assertEquals("c", filterText())
        tag("filter").assertIsFocused()
        assertEquals(layout, Store.config.value)
    }

    @Test fun aRowOfThreeOrFourLinesHasTheAirAboveAndBelowItsWordsThatARowOfTwoHas() {
        page(null, *bar)
        /** A type's row and the block its words make in it: the air above, the air below, and how many lines of help it has. */
        fun air(type: String): Triple<Float, Float, Float> {
            val row = tag("type:$type").fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }
            val words = rule.onAllNodes(within("type:$type") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes().map { Rect(it.positionInRoot, it.size.toSize()) }
            return Triple(words.minOf { it.top } - row.top, row.bottom - words.maxOf { it.bottom }, words.maxOf { it.bottom } - words.minOf { it.top })
        }
        val two = air("storage")
        assertEquals("a row of a name and one line is the least a row is", 64f, bottom("type:storage") - top("type:storage"), 0.5f)
        assertEquals(two.first, two.second, 1f)
        // Heat says what it is in a line or two and where its + puts it in one more.
        for (type in listOf("heat", "devices", "event")) {
            val tall = air(type)
            assertTrue("$type has more lines than Storage", tall.third > two.third + 8f)
            assertEquals("$type, above its words", two.first, tall.first, 1f)
            assertEquals("$type, below its words", two.second, tall.second, 1f)
        }
    }

    @Test fun aQuietTypesPlusLandsItUnderTheLineWithItsRuleOnAndItsRowSaysSoBeforeAndAfter() {
        page(null, *bar)
        val heat = Items.of("heat")!!
        val rule0 = heat.trigger!!.short(fresh("x", "heat"))
        rule.onNode(hasTestTag("adds") and within("type:heat")).assertTextEquals(str(R.string.library_adds_when_active, rule0))
        assertTrue(str(R.string.library_adds_when_active, rule0).startsWith("Adds to When active · "))
        // A type that is always in the bar says nothing of the kind.
        rule.onNode(hasTestTag("adds") and within("type:memory")).assertDoesNotExist()

        click("add:heat")
        val new = Store.config.value.items.single { it.type == "heat" }
        assertTrue(new.whenActive)
        assertEquals(Section.HIDDEN, new.section)
        assertTrue("under the line", top("row:${new.id}") > top("part-line"))
        rule.onNode(hasText(str(R.string.add_added_rule, rule0)) and within("type:heat")).assertExists()
        rule.onNode(hasText(str(R.string.library_in_bar)) and within("type:heat")).assertExists()
        rule.onNodeWithTag("added-mark", useUnmergedTree = true).assertExists()
    }

    @Test fun withTheArrowAQuietTypesRowSaysItAddsBehindIt() {
        page(null, *bar, mode = HiddenMode.CLICK)
        val rule0 = Items.of("heat")!!.trigger!!.short(fresh("x", "heat"))
        val row = rule.onNode(hasTestTag("adds") and within("type:heat"))
        row.assertTextEquals(str(R.string.library_adds_behind, rule0))
        assertTrue(str(R.string.library_adds_behind, rule0).startsWith("Adds behind ‹ · "))
        row.assertContentDescriptionEquals(str(R.string.library_adds_behind, rule0).replace("‹", str(R.string.chevron_spoken)))

        click("add:heat")
        val new = Store.config.value.items.single { it.type == "heat" }
        assertTrue(new.whenActive)
        assertEquals(Section.HIDDEN, new.section)
    }

    @Test fun afterRemoveInOptionsTheLibraryIsUpAndTheLineIsOnScreen() {
        // The last type of the last group: far below what the panel shows.
        val last = Items.inGroup(ItemGroup.entries.last()).last()
        val setUp = fresh("s", last.type).copy(display = Display.TEXT)
        page("s", *bar, setUp)
        tag("options").assertExists()
        tag("library").assertDoesNotExist()
        remove()
        assertNull(selected)
        tag("library").assertExists()
        switchPart(R.string.page_add).assertIsSelected()
        tag("line:s").assertIsDisplayed()
        rule.onNode(hasText(str(R.string.row_text_only)) and within("line:s")).assertIsDisplayed()
    }

    @Test fun anItemRemovedByTheBarsOwnMenuIsLetGoSoItsLinesPlusDoesNotTurnThePanel() {
        page("k", *bar, clockIn("k", "Asia/Tokyo"))
        tag("options").assertExists()
        // The bar's right-click menu, while the settings show the item's options.
        rule.runOnIdle { Store.update { it.removed("k") } }
        rule.waitForIdle()
        assertNull("only an item in the bar is selected", selected)
        tag("options").assertDoesNotExist()
        tag("line:k").assertExists()

        click("add-line:k")
        assertEquals(Section.SHOWN, now("k")?.section)
        assertNull("a + never selects", selected)
        tag("options").assertDoesNotExist()
        tag("library").assertExists()
    }

    @Test fun belowTheWidthWithRoomForBothRemoveInOptionsTurnsToAddItemsAndTheLine() {
        val layout = page("k", *bar, clockIn("k", "Asia/Tokyo"), width = 960)
        remove()
        switchPart(R.string.page_add).assertIsSelected()
        tag("line:k").assertIsDisplayed()
        // The slip waits on "Your bar", one part to the left.
        switchPart(R.string.page_bar).performClick()
        rule.waitForIdle()
        rule.onNode(hasText(str(R.string.bar_undo)) and hasClickAction() and within("bar-list")).performClick()
        rule.waitForIdle()
        assertEquals(layout, Store.config.value)
        switchPart(R.string.page_add).performClick()
        rule.waitForIdle()
        lines.assertCountEquals(0)
    }

    @Test fun removeEmptiesAFilterThatWouldHideTheLine() {
        page("k", *bar, clockIn("k", "Asia/Tokyo"))
        rule.onNode(hasText(str(R.string.page_add)) and within("panel-switch")).performClick()
        rule.waitForIdle()
        tag("filter").performTextInput("zzz")
        rule.waitForIdle()
        tag("type:clock").assertDoesNotExist()
        rule.onNode(hasText(title("clock")) and within("panel-switch")).performClick()
        rule.waitForIdle()
        remove()
        assertEquals("", filterText())
        tag("line:k").assertIsDisplayed()
    }

    @Test fun anItemOfATypeThisVersionDoesNotKnowLeavesTheLayoutAndUndoRestoresIt() {
        val layout = page("u", *bar, ItemConfig("u", "from-a-newer-version", options = mapOf("k" to "1")))
        remove()
        assertNull("the library can't keep it", now("u"))
        tag("library").assertExists()
        lines.assertCountEquals(0)
        rule.onNode(hasText(str(R.string.bar_undo)) and hasClickAction() and within("bar-list")).performClick()
        rule.waitForIdle()
        assertEquals(layout, Store.config.value)
        tag("row:u").assertExists()
    }

    @Test fun anOffItemFromAnOlderLayoutShowsAsALineByTheRule() {
        // Up to 1.8 any item could be turned off: one that carries a setup is a line, one as it came is not kept.
        val layout = BarConfig(items = listOf(*bar, fresh("t", "timer").with("label", "Tea").copy(section = Section.OFF),
            fresh("m", "storage").copy(section = Section.OFF)), position = Position.RIGHT)
        page(null, *layout.items.toTypedArray())
        assertEquals(listOf("a", "b", "t"), ids())
        lines.assertCountEquals(1)
        rule.onNode(hasText("Tea") and within("line:t")).assertExists()
    }
}

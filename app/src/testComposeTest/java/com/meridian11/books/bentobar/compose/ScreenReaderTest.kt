package com.meridian11.books.bentobar.compose

import android.app.Application
import android.graphics.Rect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.Strip
import com.meridian11.books.bentobar.bar.StripEntry
import com.meridian11.books.bentobar.bar.StripEvents
import com.meridian11.books.bentobar.bar.StripLook
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Pill
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.TextSize
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.ui.BarPage
import com.meridian11.books.bentobar.ui.ChipRow
import com.meridian11.books.bentobar.ui.LocalWindowLayout
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.SectionLabel
import com.meridian11.books.bentobar.ui.SliderRow
import com.meridian11.books.bentobar.ui.Undo
import com.meridian11.books.bentobar.ui.windowLayout
import com.meridian11.books.bentobar.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What a screen reader finds on the shared pieces, and that a large text size only grows what is words (findings F37, F86, F36). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class ScreenReaderTest {
    @get:Rule val rule = createComposeRule()

    @Before fun env() = Env.init(ApplicationProvider.getApplicationContext<Application>())

    @Test fun aSectionLabelIsAHeading() {
        rule.setContent { SectionLabel("Options") }
        rule.onNodeWithText("OPTIONS").assert(isHeading())
    }

    @Test fun anOnOffEntrySaysItsStateAndItsMarkIsNotReadOut() {
        rule.setContent { MenuEntry("x", "Show when active", detail = "✓", checked = true) {} }
        rule.onNodeWithText("Show when active", useUnmergedTree = false).assertIsOn()
        rule.onNodeWithText("✓").assertDoesNotExist()
    }

    @Test fun aChosenChipIsSelectedAndItsCheckMarkIsNotReadOut() {
        rule.setContent { ChipRow(listOf("Icon", "Text"), selected = 1) {} }
        rule.onNodeWithText("Text").assertIsSelected()
        rule.onNodeWithText("✓ ").assertDoesNotExist()
        rule.onNodeWithText("✓", substring = true).assertDoesNotExist()
    }

    @Test fun aSliderSaysItsValueInItsOwnUnit() {
        rule.setContent { SliderRow("Spacing", 12, 0..24, { "$it dp" }) {} }
        rule.onNodeWithContentDescription("Spacing").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "12 dp"))
    }

    @Test fun theFormatsTakeTheirWordsFromTheAppsText() {
        // Env.init hands Fmt the words from the text resources: a resource format that doesn't fit its call shows up here.
        assertEquals("85%", Fmt.percent(85))
        assertEquals("840 KB", Fmt.bytes(840_000.0))
        assertEquals("1 hour 5 minutes", Fmt.spoken(65 * 60_000L))
        assertEquals("1h 05m", Fmt.duration(65 * 60_000L))
    }

        private object Events : StripEvents {
        override fun click(item: ItemConfig, at: Rect) {}
        override fun context(item: ItemConfig, at: Rect) {}
        override fun scroll(item: ItemConfig, steps: Int) {}
        override fun chevron(at: Rect) {}
        override fun chevronContext(at: Rect) {}
        override fun hover(inside: Boolean) {}
        override fun placed(id: String, at: Rect) {}
    }

    @Test fun twiceTheTextSizeLeavesADateBadgeAndItsDigitsAsTheyAre() {
        val look = StripLook(Color.White, lightText = true, TextSize.DEFAULT, 8.dp, Pill.NONE)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Strip(listOf(StripEntry(ItemConfig("d", "calendar"), ItemState(dayNumber = 9, desc = "Today"))), emptyList(), showChevron = false, chevronAlways = false,
                    chevronReservePx = 0, expanded = false, chevronOnLeft = false, look = look, maxWidthPx = 500, heightDp = 36.dp, events = Events)
            }
        }
        // 15 dp of badge + 1 + the item's 6 dp at each end, whatever the text size.
        val item = rule.onNodeWithContentDescription("Today").getBoundsInRoot()
        assertEquals(28f, (item.right - item.left).value, 1f)
        // The digits fit the 16 dp badge: in sp, at 200% their line would be cut off by it.
        val digits = rule.onNodeWithText("9", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("digits of ${digits.bottom - digits.top} in a 16 dp badge", (digits.bottom - digits.top).value < 14f)
    }

    // The bar's rows: every command a row has by pointer or key is an action of the row, and only where it would do something.

    private fun str(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)
    private fun actions(id: String) = rule.onNodeWithTag("row:$id").fetchSemanticsNode().config[SemanticsActions.CustomActions]
    private fun labels(id: String) = actions(id).map { it.label }
    private fun act(id: String, label: Int) {
        val action = actions(id).first { it.label == str(label) }
        rule.runOnUiThread { assertTrue(action.action()) }
        rule.waitForIdle()
    }

    /** The Bar page as one pane, so the list is on screen at the test's small size, with the layout put back after [check]. */
    private fun barRows(mode: HiddenMode, vararg items: ItemConfig, check: () -> Unit) {
        val before = Store.config.value
        Undo.last.value = null
        try {
            Store.update { BarConfig(items = items.toList(), position = Position.RIGHT, hiddenMode = mode) }
            rule.setContent {
                CompositionLocalProvider(LocalWindowLayout provides windowLayout(700)) { BarPage(null, onSelect = {}, onAdded = {}) }
            }
            rule.waitForIdle()
            check()
        } finally {
            Store.update { before }
            Undo.last.value = null
        }
    }

    @Test fun aRowsActionsAreTheCommandsItCanTakeAndNoOthers() {
        val up = str(R.string.row_move_up); val down = str(R.string.row_move_down); val remove = str(R.string.detail_remove)
        val active = str(R.string.row_show_when_active)
        barRows(HiddenMode.SHOW_ALL, ItemConfig("e", "event"), ItemConfig("a", "clock"), ItemConfig("b", "memory"), ItemConfig("s", "sound")) {
            assertEquals("the first row that moves has no way up", listOf(down, active, remove), labels("a"))
            assertEquals(listOf(up, down, active, remove), labels("b"))
            assertEquals("a type with no rule can't go under the line, by a step or by name", listOf(up, remove), labels("s"))
            assertEquals("Next meeting is always first: it has no order to change", listOf(active, remove), labels("e"))
        }
    }

    @Test fun aRowsActionsDoWhatTheControlsDo() {
        barRows(HiddenMode.SHOW_ALL, ItemConfig("a", "clock"), ItemConfig("b", "memory"), ItemConfig("c", "battery")) {
            fun item(id: String) = Store.config.value.items.first { it.id == id }
            act("b", R.string.row_move_up)
            assertEquals(listOf("b", "a", "c"), Store.config.value.items.map { it.id })

            act("b", R.string.row_show_when_active)
            assertTrue(item("b").whenActive)
            assertEquals(Section.HIDDEN, item("b").section)
            // Under the line the same place in the list of actions offers the way back.
            assertTrue(str(R.string.row_show_always) in labels("b"))
            assertTrue(str(R.string.row_show_when_active) !in labels("b"))
            act("b", R.string.row_show_always)
            assertEquals(Section.SHOWN, item("b").section)

            act("c", R.string.detail_remove)
            assertEquals(listOf("b", "a"), Store.config.value.items.map { it.id })
            rule.onNodeWithTag("row:c").assertDoesNotExist()
            rule.onNodeWithTag("slip").assertExists()
        }
    }

    @Test fun withTheArrowTheActionPutsARowBehindItWhateverItsType() {
        barRows(HiddenMode.CLICK, ItemConfig("a", "clock"), ItemConfig("s", "sound")) {
            assertTrue(str(R.string.row_put_behind) in labels("s"))
            assertTrue(str(R.string.row_show_when_active) !in labels("s"))
            act("s", R.string.row_put_behind)
            assertEquals(Section.HIDDEN, Store.config.value.items.first { it.id == "s" }.section)
            assertTrue(str(R.string.row_show_always) in labels("s"))
        }
    }
}

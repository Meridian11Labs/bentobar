package com.meridian11.books.bentobar.compose

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Part
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.data.partOf
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.ui.BarPage
import com.meridian11.books.bentobar.ui.LocalWindowLayout
import com.meridian11.books.bentobar.ui.SettingsTheme
import com.meridian11.books.bentobar.ui.Undo
import com.meridian11.books.bentobar.ui.windowLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * The Bar page's one drag, by a mouse and by a finger that are pressed, moved and let go: what a drop writes, what the
 * page shows while something is carried, and what puts everything back. How it feels in the hand no test can say.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1200dp-h800dp-mdpi")
class DragTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var before: BarConfig
    private lateinit var inputMode: InputModeManager
    private var selected by mutableStateOf<String?>(null)

    @Before fun env() { Env.init(app); before = Store.config.value; Undo.last.value = null }
    @After fun forget() { Store.update { before }; Undo.last.value = null }

    private fun page(select: String?, vararg items: ItemConfig): BarConfig {
        val layout = BarConfig(items = items.toList(), position = Position.RIGHT)
        Store.update { layout }
        selected = select
        rule.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalWindowLayout provides windowLayout(1180)) {
                SettingsTheme(dark = false) { BarPage(selected, onSelect = { selected = it }) }
            }
        }
        rule.waitForIdle()
        return layout
    }

    private fun item(id: String, type: String, area: Position = Position.RIGHT) = ItemConfig(id, type, area = area)
    private val three = arrayOf(item("a", "clock"), item("b", "memory"), item("c", "battery"))

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private fun title(type: String) = str(Items.of(type)!!.titleRes)
    private fun tag(tag: String) = rule.onNodeWithTag(tag)
    private fun at(tag: String): Rect = tag(tag).fetchSemanticsNode().boundsInRoot
    private fun now(id: String) = Store.config.value.items.firstOrNull { it.id == id }
    private fun order() = Store.config.value.items.filter { it.section != Section.OFF }.map { it.id }
    private fun says(text: String) = assertEquals("the line under the preview", text,
        tag("preview-line").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text })
    /** A row's handle: the six dots at its start. */
    private fun grip(id: String) = at("row:$id").let { Offset(it.left + 32f, it.center.y) }
    /** A line of the library, left of its buttons. */
    private fun line(tag: String) = at(tag).let { Offset(it.left + 80f, it.center.y) }

    /** Press at [from] and carry to [to] in steps, without letting go. */
    private fun hold(from: Offset, to: Offset) {
        rule.onRoot().performMouseInput {
            moveTo(from)
            press()
            for (i in 1..6) moveTo(from + (to - from) * (i / 6f))
        }
        rule.waitForIdle()
    }
    private fun carryTo(to: Offset) { rule.onRoot().performMouseInput { moveTo((currentPosition + to) / 2f); moveTo(to) }; rule.waitForIdle() }
    private fun letGo() { rule.onRoot().performMouseInput { release() }; rule.waitForIdle() }
    private fun drag(from: Offset, to: Offset) { hold(from, to); letGo() }

    // ---- What a drop writes ----

    @Test fun aRowDroppedOnTheLibraryIsRemovedAndNothingIsSelectedAndThePanelStays() {
        page(null, *three)
        hold(at("row:c").center, at("library").center)
        says(str(R.string.bar_drop_off, title("battery")))
        tag("row:c").assertDoesNotExist()
        assertEquals("nothing is written before the drop", listOf("a", "b", "c"), order())
        letGo()

        assertEquals(listOf("a", "b"), order())
        assertNull("a drag never selects", selected)
        tag("options").assertDoesNotExist()
        tag("library").assertExists()
        tag("slip").assertExists()
        assertEquals("one step of Undo", "c", Undo.last.value?.removed)
        rule.runOnIdle { Undo.undo() }
        rule.waitForIdle()
        assertEquals(listOf("a", "b", "c"), order())
    }

    @Test fun aRowDroppedOnAnotherItemsOptionsIsRemovedAndTheSelectionAndThePanelStay() {
        page("a", *three)
        tag("options").assertExists()
        hold(at("row:c").center, at("panel").center)
        assertEquals("with Options up the card's first row is the well, in its own place", at("options-head"), at("well"))
        letGo()

        assertEquals(listOf("a", "b"), order())
        assertEquals("a", selected)
        tag("row:a").assertIsSelected()
        tag("options").assertExists()
        tag("library").assertDoesNotExist()
        tag("well").assertDoesNotExist()
    }

    @Test fun aRowIsCarriedIntoThePartUnderTheLineAndOneWithoutARuleIsToldWhyNot() {
        page(null, item("a", "clock"), item("s", "sound"), item("c", "battery"))
        val under = Offset(at("row:a").center.x, at("part-line").bottom + 20f)
        drag(at("row:c").center, under)
        assertEquals(Part.LOWER, Store.config.value.partOf(now("c")!!))
        assertEquals("c", Undo.last.value?.let { "c" })

        val kept = Store.config.value
        Undo.last.value = null
        hold(at("row:s").center, Offset(at("row:a").center.x, at("row:c").center.y))
        says(str(R.string.visibility_no_rule_drop, title("sound")))
        letGo()
        assertEquals("a part that refuses a row changes nothing", kept, Store.config.value)
        assertNull(Undo.last.value)
        says(str(R.string.visibility_no_rule_drop, title("sound")))
    }

    @Test fun aRowCrossesToAnotherLaneAtThePlaceUnderThePointerInOneStepOfUndo() {
        val layout = page(null, item("a", "clock", Position.LEFT), item("l", "memory", Position.LEFT), item("b", "storage"), item("c", "battery"))
        val a = at("row:a")
        hold(at("row:c").center, Offset(a.center.x, a.bottom))
        says(str(R.string.bar_drop_move, title("battery"), str(R.string.areas_drop_in, title("clock"), str(R.string.areas_left))))
        letGo()
        assertEquals(Position.LEFT, now("c")?.area)
        assertEquals(listOf("a", "c", "l"), Store.config.value.items.filter { it.area == Position.LEFT }.map { it.id })
        rule.runOnIdle { Undo.undo() }
        rule.waitForIdle()
        assertEquals(layout, Store.config.value)
    }

    @Test fun aLaneThatEmptiesByADropClosesWithItsSlipAndUndoBringsBothBack() {
        val layout = page(null, item("a", "clock", Position.LEFT), item("b", "memory"), item("c", "battery"))
        val b = at("row:b")
        drag(at("row:a").center, Offset(b.center.x, b.top + 4f))
        assertEquals(Position.RIGHT, now("a")?.area)
        tag("fold").assertExists()
        rule.runOnIdle { Undo.undo() }
        rule.waitForIdle()
        assertEquals(layout, Store.config.value)
        tag("fold").assertDoesNotExist()
    }

    @Test fun aTypeOfTheLibraryIsDroppedAtAPlaceAndARememberedLineComesBackAtOne() {
        val tokyo = Items.of("clock")!!.fresh("k", Position.RIGHT).with("zone", "Asia/Tokyo").copy(section = Section.OFF)
        page(null, item("a", "memory"), item("b", "battery"), tokyo)
        val between = Offset(at("row:a").center.x, at("row:a").bottom)
        hold(line("type:storage"), between)
        tag("well").assertDoesNotExist()
        says(str(R.string.bar_drop_add, title("storage"), title("memory")))
        assertEquals("nothing is added before the drop", 3, Store.config.value.items.size)
        letGo()
        assertEquals(listOf("memory", "storage", "battery"), Store.config.value.items.filter { it.section != Section.OFF }.map { it.type })
        assertNull("a drag never selects", selected)
        assertNotNull("one step of Undo", Undo.last.value)

        tag("line:k").performScrollTo()
        hold(line("line:k"), Offset(at("row:a").center.x, at("row:a").top + 4f))
        says(str(R.string.bar_drop_add_first, title("clock"), title("memory")))
        letGo()
        assertEquals("k", order().first())
        assertEquals(tokyo.options, now("k")?.options)
        tag("line:k").assertDoesNotExist()
    }

    @Test fun aRowDroppedOnAnAreaOfThePreviewThatHasNoItemsStartsIt() {
        page(null, *three)
        hold(at("row:a").center, at("preview").let { Offset(it.center.x, it.top + 26f) })
        val left = at("zone:LEFT")
        carryTo(Offset(left.center.x, left.center.y))
        says(str(R.string.areas_drop_starts, title("clock"), str(R.string.areas_left)))
        letGo()
        assertEquals(Position.LEFT, now("a")?.area)
        assertEquals(listOf(Position.RIGHT, Position.RIGHT), listOf(now("b")?.area, now("c")?.area))
        tag("bracket:LEFT").assertExists()
    }

    // ---- What the page shows meanwhile ----

    @Test fun atPickupTheWellStandsInTheFilterFieldsPlaceAndNothingInTheLibraryMoves() {
        page(null, *three)
        val filter = at("filter")
        val group = at("group:SYSTEM")
        tag("well").assertDoesNotExist()

        hold(at("row:b").center, at("row:b").center + Offset(0f, 30f))
        assertEquals("the well has the filter field's exact place", filter, at("well"))
        assertEquals("the first group's heading has not moved", group, at("group:SYSTEM"))
        letGo()
        tag("well").assertDoesNotExist()
        assertEquals(group, at("group:SYSTEM"))

        // What comes from the library can't be dropped on it: no well.
        hold(line("type:storage"), at("row:b").center)
        tag("well").assertDoesNotExist()
        carryTo(at("library").center)
        tag("well").assertDoesNotExist()
        says(str(R.string.bar_drop_none))
        letGo()
        assertEquals("let go over the library, a type is not added", listOf("a", "b", "c"), order())
    }

    @Test fun withOneLaneTheAreasShowOnlyWhileThePointerIsOverThePreviewAndNothingChangesHeight() {
        page(null, *three)
        val preview = at("preview")
        val list = at("bar-list")
        fun zones() = Position.entries.count { area -> rule.onAllNodes(hasTestTag("zone:$area")).fetchSemanticsNodes().isNotEmpty() }
        fun brackets() = Position.entries.count { area -> rule.onAllNodes(hasTestTag("bracket:$area")).fetchSemanticsNodes().isNotEmpty() }
        fun same(whenWhat: String) {
            assertEquals("the preview's height $whenWhat", preview.height, at("preview").height)
            assertEquals("the list's place $whenWhat", list.top, at("bar-list").top)
        }

        hold(at("row:b").center, at("row:b").center + Offset(0f, 30f))
        assertEquals("no zone at pickup", 0, zones())
        assertEquals("no bracket at pickup", 0, brackets())
        tag("drag-row").assertExists()
        tag("drag-chip").assertDoesNotExist()
        same("at pickup")

        carryTo(Offset(preview.center.x, preview.top + 26f))
        assertEquals("every area is a zone while the pointer is over the preview", 3, zones())
        assertEquals(3, brackets())
        tag("drag-chip").assertExists()
        tag("drag-row").assertDoesNotExist()
        assertTrue("over the preview the copy is the item's chip, not a row", at("drag-chip").height < at("row:a").height && at("drag-chip").width < 200f)
        same("over the preview")

        carryTo(at("row:a").center)
        assertEquals("gone again after the pointer leaves", 0, zones())
        assertEquals(0, brackets())
        tag("drag-row").assertExists()
        same("back over the list")
        letGo()
        same("after the drop")
    }

    @Test fun withSeveralLanesTheAreasShowFromThePickup() {
        page(null, item("a", "clock", Position.LEFT), item("b", "memory"), item("c", "battery"))
        hold(at("row:b").center, at("row:b").center + Offset(0f, 30f))
        for (area in Position.entries) tag("zone:$area").assertExists()
        for (area in Position.entries) tag("bracket:$area").assertExists()
        letGo()
        tag("zone:CENTER").assertDoesNotExist()
        tag("bracket:CENTER").assertDoesNotExist()
    }

    // ---- What puts everything back ----

    @Test fun escPutsEverythingBackWithNoChangeAndNoUndo() {
        val layout = page("c", *three)
        rule.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        tag("row:c").requestFocus()
        hold(at("row:a").center, at("row:b").center + Offset(0f, 12f))
        tag("drag-row").assertExists()
        says(str(R.string.bar_drop_move, title("clock"), title("memory")))

        tag("bar-list").performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        tag("drag-row").assertDoesNotExist()
        assertEquals(layout, Store.config.value)
        assertNull(Undo.last.value)
        assertEquals("Esc ended the drag, not the selection", "c", selected)
        letGo()
        assertEquals("the button let go later drops nothing", layout, Store.config.value)
        assertNull(Undo.last.value)
        assertTrue(at("row:a").top < at("row:b").top)
    }

    @Test fun aDropOutsideChangesNothingAndLeavesNoUndo() {
        val layout = page(null, *three)
        val list = at("bar-list")
        hold(at("row:a").center, Offset(list.right + 6f, list.center.y))
        says(str(R.string.bar_drop_none))
        letGo()
        assertEquals(layout, Store.config.value)
        assertNull(Undo.last.value)
        assertNull(selected)
        tag("drag-row").assertDoesNotExist()
    }

    // ---- Touch ----

    @Test fun aFingerPicksARowUpOnlyByItsHandle() {
        val layout = page(null, *three)
        val a = at("row:a")
        rule.onRoot().performTouchInput { down(a.center); repeat(4) { moveBy(Offset(0f, 16f)) } }
        rule.waitForIdle()
        tag("drag-row").assertDoesNotExist()
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals("a finger on the row's words carries nothing", layout, Store.config.value)

        rule.onRoot().performTouchInput { down(grip("a")); repeat(4) { moveBy(Offset(0f, 16f)) } }
        rule.waitForIdle()
        tag("drag-row").assertExists()
        says(str(R.string.bar_drop_move_touch, title("clock"), title("memory")))
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf("b", "a", "c"), order())
        assertNull("a pickup never selects", selected)
    }

    @Test fun aFingerPicksALineOfTheLibraryUpOnlyAfterALongPress() {
        page(null, *three)
        val from = line("type:storage")
        val to = Offset(at("row:a").center.x, at("row:a").bottom)
        rule.onRoot().performTouchInput { down(from); moveBy(Offset(0f, 12f)); moveBy(Offset(0f, 12f)) }
        rule.waitForIdle()
        tag("drag-row").assertDoesNotExist()
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals("a finger that moves first scrolls", listOf("a", "b", "c"), order())

        rule.onRoot().performTouchInput { down(from) }
        rule.mainClock.advanceTimeBy(700)
        rule.onRoot().performTouchInput { moveBy(Offset(-4f, 0f)) }
        rule.waitForIdle()
        tag("drag-row").assertExists()
        rule.onRoot().performTouchInput { for (i in 1..6) moveTo(from + (to - from) * (i / 6f)) }
        rule.waitForIdle()
        says(str(R.string.bar_drop_add_touch, title("storage"), title("clock")))
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf("clock", "storage", "memory", "battery"), Store.config.value.items.map { it.type })
    }
}

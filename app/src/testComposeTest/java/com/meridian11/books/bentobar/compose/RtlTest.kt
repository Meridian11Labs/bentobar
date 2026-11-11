package com.meridian11.books.bentobar.compose

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.ui.BarPage
import com.meridian11.books.bentobar.ui.Bento
import com.meridian11.books.bentobar.ui.LocalWindowLayout
import com.meridian11.books.bentobar.ui.Segment
import com.meridian11.books.bentobar.ui.SegmentPart
import com.meridian11.books.bentobar.ui.SettingsTheme
import com.meridian11.books.bentobar.ui.Undo
import com.meridian11.books.bentobar.ui.windowLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The window from right to left. The page is mirrored as every page is; what stands for the bar is not, because Left,
 * Middle and Right are sides of the screen: the lanes, the choices of an area, the preview and its brackets keep Left
 * on the left, so Alt with an arrow moves a row the way the arrow points.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1180dp-h780dp-mdpi")
class RtlTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var before: BarConfig
    private lateinit var inputMode: InputModeManager
    private var selected by mutableStateOf<String?>(null)

    @Before fun env() { Env.init(app); before = Store.config.value; Undo.last.value = null }
    @After fun forget() { Store.update { before }; Undo.last.value = null }

    private fun item(id: String, type: String, area: Position) = ItemConfig(id, type, area = area)
    private val three = listOf(item("w", "weather", Position.LEFT), item("t", "timer", Position.CENTER), item("a", "clock", Position.RIGHT), item("m", "memory", Position.RIGHT))

    private fun page(select: String?, direction: LayoutDirection = LayoutDirection.Rtl) {
        Store.update { BarConfig(items = three, position = Position.RIGHT) }
        selected = select
        rule.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalLayoutDirection provides direction, LocalWindowLayout provides windowLayout(1180)) {
                SettingsTheme(dark = false) { BarPage(selected, onSelect = { selected = it }) }
            }
        }
        rule.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        if (select != null) { rule.onNodeWithTag("row:$select").requestFocus(); rule.onNodeWithTag("row:$select").assertIsFocused() }
    }

    private fun str(id: Int) = app.getString(id)
    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun now(id: String) = Store.config.value.items.first { it.id == id }
    private fun alt(key: Key) { rule.onNodeWithTag("bar-list").performKeyInput { withKeyDown(Key.AltLeft) { pressKey(key) } }; rule.waitForIdle() }

    @Test fun thePageIsMirroredAndTheLanesKeepTheBarsSides() {
        page(null)
        // The page: the panel is at the window's start, which is now its right.
        assertTrue("the panel stands where the page starts", bounds("panel").left < bounds("bar-list").left)
        // The bar: Left's lane is on the left, as its strip is in the bar.
        assertTrue(bounds("row:w").left < bounds("row:t").left)
        assertTrue(bounds("row:t").left < bounds("row:a").left)
        assertTrue(bounds("bracket:LEFT").left < bounds("bracket:CENTER").left)
        assertTrue(bounds("bracket:CENTER").left < bounds("bracket:RIGHT").left)
    }

    @Test fun altWithAnArrowMovesARowTheWayTheArrowPointsOnScreen() {
        page("t")
        val from = bounds("row:t").left
        alt(Key.DirectionRight)
        assertEquals(Position.RIGHT, now("t").area)
        assertTrue("→ moved it to the right on screen", bounds("row:t").left > from)
        alt(Key.DirectionLeft)
        alt(Key.DirectionLeft)
        assertEquals(Position.LEFT, now("t").area)
        assertTrue("← moved it to the left on screen", bounds("row:t").left < from)
        rule.onNodeWithTag("row:t").assertIsFocused()
    }

    @Test fun theChoicesOfAnAreaKeepLeftOnTheLeft() {
        page("t")
        fun part(text: Int) = rule.onNode(hasText(str(text)) and hasAnyAncestor(hasTestTag("area"))).fetchSemanticsNode().boundsInRoot.left
        assertTrue("Area: Left, Middle, Right as they are in the bar", part(R.string.areas_left) < part(R.string.areas_middle) && part(R.string.areas_middle) < part(R.string.areas_right))
    }

    @Test fun theLibrarysAddToKeepsLeftOnTheLeft() {
        page(null)
        fun part(text: Int) = rule.onNode(hasContentDescription("${str(R.string.areas_add_to)} ${str(text)}") and hasAnyAncestor(hasTestTag("add-to"))).fetchSemanticsNode().boundsInRoot.left
        assertTrue(part(R.string.areas_left) < part(R.string.areas_middle) && part(R.string.areas_middle) < part(R.string.areas_right))
    }

    @Test fun englishWordsReadFromLeftToRightAndAreSetToTheRight() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SettingsTheme(dark = false) { Text("Behind ‹.", Modifier.width(300.dp).testTag("words"), style = Bento.type.small) }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("words").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val layout = results.single()
        assertTrue("the full stop ends the sentence on its right", layout.getBoundingBox(8).left > layout.getBoundingBox(0).left)
        assertTrue("the arrow's sign follows its word", layout.getBoundingBox(7).left > layout.getBoundingBox(5).left)
        assertEquals("the line ends at the right edge", layout.size.width.toFloat(), layout.getLineRight(0), 1f)
        assertTrue(layout.getLineLeft(0) > layout.size.width / 2f)
    }

    @Test fun aSegmentsRaisedPartIsUnderTheChosenOneInBothDirections() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var raised = 0
        var sunk = 0
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                SettingsTheme(dark = false) {
                    raised = Bento.colors.raised.toArgb()
                    sunk = Bento.colors.sunk.toArgb()
                    Box(Modifier.padding(20.dp)) { Segment(listOf(SegmentPart("One"), SegmentPart("Two")), 0, {}, Modifier.width(300.dp).testTag("segment")) }
                }
            }
        }
        // The ground just inside a part's upper corner region, clear of its words.
        fun ground(text: String): Int {
            val at = rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("segment"))).fetchSemanticsNode().boundsInRoot
            return rule.runOnIdle {
                val picture = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
                picture.getPixel((at.left + 12).toInt(), at.center.y.toInt())
            }
        }
        assertEquals("the first part is raised", raised, ground("One"))
        assertEquals(sunk, ground("Two"))
        direction = LayoutDirection.Rtl
        rule.waitForIdle()
        val one = rule.onNode(hasText("One") and hasAnyAncestor(hasTestTag("segment"))).fetchSemanticsNode().boundsInRoot
        val two = rule.onNode(hasText("Two") and hasAnyAncestor(hasTestTag("segment"))).fetchSemanticsNode().boundsInRoot
        assertTrue("an ordinary choice is mirrored", one.left > two.left)
        assertEquals("the raised part went with it", raised, ground("One"))
        assertEquals(sunk, ground("Two"))
    }
}

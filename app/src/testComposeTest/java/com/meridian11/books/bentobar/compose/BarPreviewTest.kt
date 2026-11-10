package com.meridian11.books.bentobar.compose

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Pill
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.ui.BarPreview
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
 * The picture of the bar on the Bar page: where its areas' strips stand between the mock clock and the mock system icons,
 * and what it says about the items it has no room for. Measured from the clock and the icons, which the picture draws
 * itself, so the numbers hold whatever the time is.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1200dp-h800dp-mdpi")
class BarPreviewTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var before: BarConfig

    @Before fun env() { Env.init(app); before = Store.config.value }
    @After fun forget() = Store.update { before }

    /** What an item says, by id; one without words here says its own id. */
    private var words by mutableStateOf(emptyMap<String, String>())
    /** Items that draw nothing: no icon and no words, only a name for a screen reader. */
    private var quiet = emptySet<String>()

    private fun item(id: String, area: Position, section: Section = Section.SHOWN) = ItemConfig(id, "cpu", section, area = area)
    private fun wide(vararg ids: String) = ids.associateWith { "Some more words $it" }

    private fun show(items: List<ItemConfig>, width: Dp = 1000.dp, direction: LayoutDirection = LayoutDirection.Ltr, change: BarConfig.() -> BarConfig = { this }) {
        Store.update { BarConfig(items = items).change() }
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.requiredWidth(width)) {
                    BarPreview(items.associate { it.id to if (it.id in quiet) ItemState(desc = "${it.id} desc") else ItemState(icon = "X", text = words[it.id] ?: it.id, desc = "${it.id} desc") }, null) {}
                }
            }
        }
        rule.waitForIdle()
    }

    private fun at(id: String): DpRect = rule.onNodeWithContentDescription("$id desc").getBoundsInRoot()
    /** An item the strip has no room for keeps its node and is not placed: only this tells the two apart. */
    private fun drawn(id: String) = rule.onNodeWithContentDescription("$id desc").isDisplayed()
    private fun clock() = rule.onNode(hasText("   ", substring = true)).getBoundsInRoot()
    /** The first of the mock system icons: the end of their row that is nearest the strip, left to right. */
    private fun icons() = rule.onNodeWithText("US").getBoundsInRoot()
    private fun more(n: Int) = app.resources.getQuantityString(R.plurals.bar_preview_more, n, n)
    private fun says(text: String) = rule.onNodeWithText(text, substring = true).assertExists()

    // The strip's own ends are 6 dp each (its pill's padding): an item is that far inside its strip.
    private val end = 6.dp
    /** The room on the two sides of a strip from [first] to [last] between the clock and the 12 dp before the icons. */
    private fun sides(first: String, last: String) = (at(first).left - end - clock().right) to (icons().left - 12.dp - (at(last).right + end))

    // ---- One area: where the one strip always stood. These numbers were taken from the preview before it knew areas. ----

    @Test fun aLayoutOnTheRightEndsTwelveDpBeforeTheSystemIconsAsItAlwaysDid() {
        show(listOf(item("a", Position.RIGHT), item("b", Position.RIGHT))) { copy(position = Position.RIGHT) }
        assertEquals(icons().left - 12.dp - end, at("b").right)
        assertEquals("the strip is as high as the picture, less 3 dp above and below", 3.dp to 49.dp, at("b").top to at("b").bottom)
        assertTrue("a before b: ${at("a")}, ${at("b")}", at("a").right < at("b").left)
        says(app.getString(R.string.bar_preview_hint))
    }

    @Test fun aLayoutOnTheLeftBeginsSixteenDpAfterTheClockAsItAlwaysDid() {
        show(listOf(item("a", Position.LEFT), item("b", Position.LEFT))) { copy(position = Position.LEFT) }
        assertEquals(clock().right + 16.dp + end, at("a").left)
        assertEquals(3.dp to 49.dp, at("a").top to at("a").bottom)
    }

    @Test fun aLayoutInTheMiddleIsCentredBetweenTheClockAndTheSystemIconsAsItAlwaysWas() {
        show(listOf(item("a", Position.CENTER), item("b", Position.CENTER))) { copy(position = Position.CENTER) }
        val (left, right) = sides("a", "b")
        // The two spacers share the room, and an odd pixel goes to the second.
        assertTrue("$left, $right", (right - left).value in 0f..1f && left.value > 100f)
    }

    @Test fun inARightToLeftLanguageAOneAreaLayoutIsMirroredWithThePictureAsItAlwaysWas() {
        show(listOf(item("a", Position.RIGHT), item("b", Position.RIGHT)), direction = LayoutDirection.Rtl) { copy(position = Position.RIGHT) }
        // The icons are on the left there, "US" at their right end, and the strip stands beside them.
        assertEquals(icons().right + 12.dp + end, at("a").left)
        assertTrue("the strip's own order is not mirrored: ${at("a")}, ${at("b")}", at("a").right < at("b").left)
    }

    @Test fun inARightToLeftLanguageAOneAreaLayoutOnTheLeftStandsBesideTheClockAsItAlwaysDid() {
        show(listOf(item("a", Position.LEFT), item("b", Position.LEFT)), direction = LayoutDirection.Rtl) { copy(position = Position.LEFT) }
        assertEquals(clock().left - 16.dp - end, at("b").right)
    }

    @Test fun aOneAreaLayoutWithNoRoomForAllKeepsItsLastItemsAndCountsTheRestAsItAlwaysDid() {
        // 600 dp wide, the strip's items may take 220 dp: one of these, and two would need about 280.
        words = wide("a", "b", "c")
        show(listOf(item("a", Position.RIGHT), item("b", Position.RIGHT), item("c", Position.RIGHT)), width = 600.dp) { copy(position = Position.RIGHT) }
        assertEquals(listOf(false, false, true), listOf("a", "b", "c").map { drawn(it) })
        assertEquals(icons().left - 12.dp - end, at("c").right)
        says(app.getString(R.string.bar_preview_hint) + " " + more(2))
        // ‹ is there for what has no room, at the strip's left.
        val chevron = rule.onNodeWithContentDescription(app.getString(R.string.chevron_show)).getBoundsInRoot()
        assertTrue("$chevron, ${at("c")}", chevron.right <= at("c").left)
    }

    @Test fun aOneAreaLayoutOnTheLeftKeepsItsFirstItemsAndHasItsChevronAtItsRight() {
        words = wide("a", "b", "c")
        show(listOf(item("a", Position.LEFT), item("b", Position.LEFT), item("c", Position.LEFT)), width = 600.dp) { copy(position = Position.LEFT) }
        assertEquals(listOf(true, false, false), listOf("a", "b", "c").map { drawn(it) })
        assertEquals(clock().right + 16.dp + end, at("a").left)
        val chevron = rule.onNodeWithContentDescription(app.getString(R.string.chevron_show)).getBoundsInRoot()
        assertTrue("$chevron, ${at("a")}", chevron.left >= at("a").right)
        says(more(2))
    }

    @Test fun hiddenItemsComeOutAtAPressOnTheChevronAndTheHintSaysSo() {
        show(listOf(item("h", Position.RIGHT, Section.HIDDEN), item("a", Position.RIGHT))) { copy(position = Position.RIGHT, hiddenMode = HiddenMode.CLICK) }
        says(app.getString(R.string.bar_preview_hint_hidden))
        assertEquals(listOf(false, true), listOf("h", "a").map { drawn(it) })
        rule.onNodeWithContentDescription(app.getString(R.string.chevron_show)).performClick()
        rule.waitForIdle()
        assertTrue("the hidden item is out, on the far side of ‹: ${at("h")}, ${at("a")}", at("h").right < at("a").left)
        assertEquals(icons().left - 12.dp - end, at("a").right)
        rule.onNodeWithContentDescription(app.getString(R.string.chevron_hide)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(false, true), listOf("h", "a").map { drawn(it) })
    }
}

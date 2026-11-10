package com.meridian11.books.bentobar.compose

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.ui.BarPage
import com.meridian11.books.bentobar.ui.LookPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where Settings says and changes an item's area: the chooser in the item's detail, the word on its row, and Look's
 * Position row, which puts every item in one area. Drawn through the Bar page and the Look page as the app draws them,
 * wide enough for the list and the detail side by side.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1200dp-h800dp-mdpi")
class AreaChoosersTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var before: BarConfig

    @Before fun env() { Env.init(app); before = Store.config.value }
    @After fun forget() = Store.update { before }

    private val left get() = app.getString(R.string.areas_left)
    private val middle get() = app.getString(R.string.areas_middle)
    private val right get() = app.getString(R.string.areas_right)

    private fun item(id: String, area: Position, section: Section = Section.SHOWN) = ItemConfig(id, "cpu", section, area = area)

    private fun barPage(selected: String?, vararg items: ItemConfig): BarConfig {
        // The old Position says Right throughout: nothing here may read it.
        val layout = BarConfig(items = items.toList(), position = Position.RIGHT)
        Store.update { layout }
        rule.setContent { BarPage(running = true, selected = selected, onSelect = {}, onSetup = {}) }
        rule.waitForIdle()
        return layout
    }

    // A row says its area's word too, and a row is what carries the moves as actions for a screen reader: a choice does not.
    private val isRow = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
    private fun choice(word: String) = rule.onNode(hasText(word) and !isRow)
    private fun rowsSaying(word: String) = rule.onAllNodes(hasText(word, substring = true) and isRow)

    @Test fun theDetailsChooserShowsTheItemsAreaAsChosenAndAPressChangesOnlyThatItemsArea() {
        val layout = barPage("a", item("a", Position.RIGHT), item("b", Position.RIGHT, Section.HIDDEN), item("c", Position.RIGHT, Section.OFF))
        rule.onNode(hasText(app.getString(R.string.areas_label))).assertExists()
        choice(right).assertIsSelected()
        choice(left).assertIsNotSelected()
        choice(middle).assertIsNotSelected()

        choice(left).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("only a's area, and its place in the list as it was",
            layout.copy(items = layout.items.map { if (it.id == "a") it.copy(area = Position.LEFT) else it }), Store.config.value)
        choice(left).assertIsSelected()
        choice(right).assertIsNotSelected()
    }

    @Test fun anItemThatIsOffHasTheChooserTooAndStaysOff() {
        val layout = barPage("c", item("a", Position.RIGHT), item("c", Position.RIGHT, Section.OFF))
        choice(right).assertIsSelected()
        choice(middle).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(layout.copy(items = layout.items.map { if (it.id == "c") it.copy(area = Position.CENTER) else it }), Store.config.value)
        choice(middle).assertIsSelected()
        // An area that holds only an item that is Off is not in use: the rows go on saying nothing about areas.
        for (word in listOf(left, middle, right)) rowsSaying(word).assertCountEquals(0)
    }

    @Test fun aRowSaysItsAreaOnlyWhileMoreThanOneAreaIsInUseAndARowInOffNever() {
        barPage(null, item("a", Position.RIGHT), item("b", Position.LEFT, Section.HIDDEN), item("c", Position.CENTER, Section.OFF))
        rowsSaying(right).assertCountEquals(1)
        rowsSaying(left).assertCountEquals(1)
        rowsSaying(middle).assertCountEquals(0)

        // One area again: the list reads as it did before there were areas.
        Store.update { it.copy(items = it.items.map { i -> if (i.id == "b") i.copy(area = Position.RIGHT) else i }) }
        rule.waitForIdle()
        for (word in listOf(left, middle, right)) rowsSaying(word).assertCountEquals(0)
    }

    @Test fun aRowOfATypeThisVersionDoesNotKnowSaysItsAreaLikeAnyOther() {
        // It counts as an area in use for Settings (Look shows no choice), so its row has to say where it is.
        barPage(null, item("a", Position.RIGHT), ItemConfig("n", "from-a-newer-version", area = Position.LEFT))
        rowsSaying(left).assertCountEquals(1)
        rowsSaying(right).assertCountEquals(1)
    }

    private fun lookPage(vararg items: ItemConfig): BarConfig {
        val layout = BarConfig(items = items.toList(), position = Position.RIGHT)
        Store.update { layout }
        rule.setContent { LookPage() }
        rule.waitForIdle()
        return layout
    }

    @Test fun lookShowsNoAreaAsChosenWhileTwoAreInUseAndAPressPutsEveryItemInThatOne() {
        val layout = lookPage(item("a", Position.RIGHT), item("b", Position.LEFT, Section.HIDDEN), item("c", Position.LEFT, Section.OFF))
        for (word in listOf(left, middle, right)) choice(word).assertIsNotSelected()

        choice(middle).performClick()
        rule.waitForIdle()
        assertEquals("every item's area, the one that is Off too, and nothing else",
            layout.copy(items = layout.items.map { it.copy(area = Position.CENTER) }), Store.config.value)
        choice(middle).assertIsSelected()
        choice(left).assertIsNotSelected()
        choice(right).assertIsNotSelected()
    }

    @Test fun lookSaysThatAChoiceMovesEveryItemOnlyWhileTheItemsAreInSeveralAreas() {
        val several = hasText(app.getString(R.string.areas_look_several))
        lookPage(item("a", Position.RIGHT), item("b", Position.LEFT, Section.HIDDEN))
        rule.onNode(several).assertExists()

        // One area: the row reads as it did before there were areas.
        choice(middle).performClick()
        rule.waitForIdle()
        rule.onNode(several).assertDoesNotExist()

        // No items: a place is chosen, and nothing is said.
        Store.update { it.copy(items = emptyList()) }
        rule.waitForIdle()
        rule.onNode(several).assertDoesNotExist()
        choice(right).assertIsSelected()
    }

    @Test fun lookShowsTheOneAreaEveryItemOutsideOffIsInWhateverTheOldPositionSays() {
        lookPage(item("a", Position.LEFT), item("b", Position.LEFT, Section.HIDDEN), item("c", Position.CENTER, Section.OFF))
        choice(left).assertIsSelected()
        choice(middle).assertIsNotSelected()
        choice(right).assertIsNotSelected()
    }
}

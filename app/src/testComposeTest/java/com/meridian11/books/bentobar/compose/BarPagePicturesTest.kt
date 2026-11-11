package com.meridian11.books.bentobar.compose

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.BarStatus
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Display
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.fresh
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.ui.BarPage
import com.meridian11.books.bentobar.ui.Page
import com.meridian11.books.bentobar.ui.Palette
import com.meridian11.books.bentobar.ui.SettingsTheme
import com.meridian11.books.bentobar.ui.Shell
import com.meridian11.books.bentobar.ui.Undo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Bar page as it is drawn, in the window, in light and dark: the list's card and the panel's are the palette's panel,
 * the selected row stands on the soft ground, and Undo's slip on the sunk one. With the environment variable
 * `BENTOBAR_PICTURES` naming a directory, each picture is also written there as a PNG, to look at beside the design.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1180dp-h780dp-mdpi")
class BarPagePicturesTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var before: BarConfig
    private lateinit var view: View
    private var dark by mutableStateOf(false)
    private var selected by mutableStateOf<String?>(null)

    @Before fun env() { Env.init(app); before = Store.config.value; Undo.last.value = null }
    @After fun forget() { Store.update { before }; Undo.last.value = null }

    private fun picture(name: String): Bitmap {
        val bitmap = rule.runOnIdle { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) } }
        System.getenv("BENTOBAR_PICTURES")?.takeIf { it.isNotBlank() }?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }

    private fun assertColor(what: String, want: Color, bitmap: Bitmap, x: Int, y: Int) =
        assertEquals(what, Integer.toHexString(want.toArgb()), Integer.toHexString(bitmap.getPixel(x, y)))

    private fun palette() = if (dark) Palette.Dark else Palette.Light

    /** A bar of seven items and two that wait for their rule. */
    private fun window(mode: HiddenMode = HiddenMode.SHOW_ALL, select: String? = null, vararg remembered: ItemConfig) {
        fun item(id: String, type: String, display: Display = Display.ICON_AND_TEXT) = ItemConfig(id, type, display = display, area = Position.RIGHT)
        fun waiting(id: String, type: String) = ItemConfig(id, type, Section.HIDDEN, whenActive = true, area = Position.RIGHT)
        Store.update {
            BarConfig(items = listOf(item("event", "event"), item("weather", "weather"), item("clock", "clock"), item("timer", "timer"),
                item("network", "network", Display.TEXT), item("cpu", "cpu"), item("sound", "sound", Display.ICON),
                waiting("media", "media"), waiting("devices", "devices")) + remembered, position = Position.RIGHT, hiddenMode = mode)
        }
        selected = select
        rule.setContent {
            view = LocalView.current
            SettingsTheme(dark) {
                Shell(Page.BAR, {}, running = true, enabled = true, status = BarStatus.SHOWN, onEnabled = {}, onTurnOn = {}, insets = WindowInsets(top = 36.dp)) {
                    BarPage(selected, onSelect = { selected = it })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test fun theListAndThePanelAreCardsOnThePagesGround() {
        window()
        for (isDark in listOf(false, true)) {
            dark = isDark
            val bitmap = picture("main-${if (isDark) "dark" else "light"}")
            val list = bounds("bar-list")
            assertColor("the page's ground beside the list", palette().surface, bitmap, list.left.toInt() - 6, list.center.y.toInt())
            assertColor("the list's card", palette().panel, bitmap, list.left.toInt() + 6, list.bottom.toInt() - 24)
        }
    }

    @Test fun theSelectedRowStandsOnTheSoftGround() {
        window(select = "network")
        for (isDark in listOf(false, true)) {
            dark = isDark
            val bitmap = picture("selected-${if (isDark) "dark" else "light"}")
            val row = bounds("row:network")
            assertColor("the selected row", palette().soft, bitmap, row.left.toInt() + 8, row.center.y.toInt())
            val other = bounds("row:cpu")
            assertColor("a row that is not selected", palette().panel, bitmap, other.left.toInt() + 8, other.center.y.toInt())
            // Between a row's label and its control.
            val showAs = bounds("show-as")
            assertColor("the panel's card", palette().panel, bitmap, showAs.left.toInt() - 4, showAs.center.y.toInt())
        }
        // The other states of the options, to look at.
        dark = false
        selected = "sound"
        picture("options-no-rule")
        selected = "event"
        picture("options-permission")
        selected = "devices"
        picture("options-when-active")
    }

    @Test fun undoStandsOnTheSunkGroundInTheRemovedRowsPlace() {
        window(select = "timer")
        val place = bounds("row:timer")
        rule.onNode(hasText(app.getString(R.string.detail_remove)) and hasClickAction()).performClick()
        rule.waitForIdle()
        for (isDark in listOf(false, true)) {
            dark = isDark
            val bitmap = picture("removed-${if (isDark) "dark" else "light"}")
            val slip = bounds("slip")
            assertEquals("where the row was", place.top, slip.top)
            // Before its words begin.
            assertColor("the slip", palette().sunk, bitmap, slip.left.toInt() - 4, slip.center.y.toInt())
        }
    }

    @Test fun withTheArrowTheLowerPartIsWhatIsBehindIt() {
        window(HiddenMode.CLICK)
        picture("chevron-light")
        rule.onNodeWithTag("row:media").performClick()
        rule.waitForIdle()
        val bitmap = picture("chevron-selected-light")
        val row = bounds("row:media")
        assertColor("the selected row under the line", palette().soft, bitmap, row.left.toInt() + 8, row.center.y.toInt())
    }

    @Config(qualifiers = "w960dp-h780dp-mdpi")
    @Test fun at960TheListIsThePanelsFirstPart() {
        window()
        picture("mid-list")
        rule.onNodeWithTag("row:network").performClick()
        rule.waitForIdle()
        val bitmap = picture("mid-options")
        val showAs = bounds("show-as")
        assertColor("the panel's card", palette().panel, bitmap, showAs.left.toInt() - 4, showAs.center.y.toInt())
    }

    /** A clock set to a city, out of the bar: a line under Clock. */
    private fun london(id: String = "london") = Items.of("clock")!!.fresh(id, Position.RIGHT).with("zone", "Europe/London").copy(section = Section.OFF)
    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo(); rule.onNodeWithTag(tag).performClick(); rule.waitForIdle() }

    @Test fun theLibraryIsOneCardWithTheFilterAndTheTypesInTheirGroups() {
        window(remembered = arrayOf(london()))
        picture("library-top")
        rule.onNodeWithTag("type:timer").performScrollTo()
        rule.waitForIdle()
        for (isDark in listOf(false, true)) {
            dark = isDark
            val bitmap = picture("library-${if (isDark) "dark" else "light"}")
            val library = bounds("library")
            assertColor("the library's card", palette().panel, bitmap, library.left.toInt() + 6, library.center.y.toInt())
            val line = bounds("line:london")
            assertColor("a remembered setup's line stands on the card", palette().panel, bitmap, line.left.toInt() + 6, line.center.y.toInt())
        }
        dark = false
        // A type added: its row says so, with the Undo, and the bar's new row is marked.
        click("add:countdown")
        picture("library-added")
        // A quiet type: under the line, and its row says when it shows.
        click("add:heat")
        picture("library-added-quiet")
        // A setup forgotten: its place says so until the next change.
        click("forget:london")
        picture("library-forgot")
    }

    @Test fun theFilterShowsWhatMatchesAndCountsIt() {
        window(remembered = arrayOf(london()))
        rule.onNodeWithTag("filter").performTextInput("clo")
        rule.waitForIdle()
        val bitmap = picture("library-filter")
        val count = bounds("filter-count")
        assertColor("the count stands on the card", palette().panel, bitmap, count.left.toInt() - 4, count.center.y.toInt())
        rule.onNodeWithTag("filter").performTextInput("zzz")
        rule.waitForIdle()
        picture("library-filter-nothing")
    }

    @Test fun afterRemoveThePanelIsTheLibraryAtTheNewLine() {
        window(select = "clock", remembered = arrayOf(london()))
        rule.onNodeWithTag("row:clock").assertExists()
        Store.updateItem("clock") { it.with("zone", "Asia/Tokyo") }
        rule.waitForIdle()
        rule.onNode(hasText(app.getString(R.string.detail_remove)) and hasClickAction()).performClick()
        rule.waitForIdle()
        picture("library-after-remove")
        rule.onNodeWithTag("line:clock").assertIsDisplayed()
    }

    @Config(qualifiers = "w960dp-h780dp-mdpi")
    @Test fun at960AddItemsIsTheSwitchsSecondPart() {
        window(remembered = arrayOf(london()))
        rule.onNode(hasText(app.getString(R.string.page_add)) and hasAnyAncestor(hasTestTag("panel-switch"))).performClick()
        rule.waitForIdle()
        val bitmap = picture("mid-library")
        val library = bounds("library")
        assertColor("the library's card", palette().panel, bitmap, library.left.toInt() + 6, library.center.y.toInt())
    }

    @Config(qualifiers = "w640dp-h780dp-mdpi")
    @Test fun at640TheLibraryTakesTheWindow() {
        window(remembered = arrayOf(london()))
        rule.onNode(hasText(app.getString(R.string.page_add)) and hasAnyAncestor(hasTestTag("panel-switch"))).performClick()
        rule.waitForIdle()
        picture("narrow-library")
        click("add:countdown")
        picture("narrow-library-added")
    }

    @Config(qualifiers = "w640dp-h780dp-mdpi")
    @Test fun at640TheRowsHaveTwoLines() {
        window()
        val bitmap = picture("narrow-list")
        val list = bounds("bar-list")
        assertColor("the list's card", palette().panel, bitmap, list.left.toInt() + 6, list.bottom.toInt() - 24)
    }
}

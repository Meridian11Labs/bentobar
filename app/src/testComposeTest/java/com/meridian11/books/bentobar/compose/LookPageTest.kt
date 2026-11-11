package com.meridian11.books.bentobar.compose

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.BarStatus
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.HiddenMode
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.ui.LookPage
import com.meridian11.books.bentobar.ui.Page
import com.meridian11.books.bentobar.ui.SettingsTheme
import com.meridian11.books.bentobar.ui.Shell
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
import java.io.File

/**
 * The Look page: one column of cards 760 wide at most, every choice of one as radio buttons beside its name, switches
 * as whole rows, and Copy and Paste answering in their own row. With the environment variable `BENTOBAR_PICTURES`
 * naming a directory, the page is also written there as PNGs, to look at beside the design.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1180dp-h780dp-mdpi")
class LookPageTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var before: BarConfig
    private lateinit var view: View
    private var dark by mutableStateOf(false)

    @Before fun env() { Env.init(app); before = Store.config.value }
    @After fun forget() = Store.update { before }

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun bounds(text: String) = rule.onNode(hasText(text)).fetchSemanticsNode().boundsInRoot
    private val clipboard get() = app.getSystemService(ClipboardManager::class.java)

    private fun picture(name: String) {
        val bitmap = rule.runOnIdle { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) } }
        System.getenv("BENTOBAR_PICTURES")?.takeIf { it.isNotBlank() }?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun show(config: BarConfig = BarConfig(items = listOf(ItemConfig("clock", "clock", area = Position.RIGHT)), position = Position.RIGHT)) {
        Store.update { config }
        rule.setContent {
            view = LocalView.current
            SettingsTheme(dark) {
                Shell(Page.LOOK, {}, running = true, enabled = true, status = BarStatus.SHOWN, onEnabled = {}, onTurnOn = {}, insets = WindowInsets(top = 36.dp)) {
                    LookPage()
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun lookIsOneColumnWhereEveryChoiceIsAListOfRadioButtonsBesideItsName() {
        show()
        val page = rule.onNode(hasTestTag("look")).fetchSemanticsNode().boundsInRoot
        val name = bounds(str(R.string.look_position))
        val left = bounds(str(R.string.areas_left))
        val middle = bounds(str(R.string.areas_middle))
        val small = bounds(str(R.string.look_text_small))
        // One column in the window's middle, 760 wide at most: the cards' rows begin 20 inside it.
        assertEquals("the column is in the middle", page.center.x, (name.left - 20f + left.right + 20f) / 2f, 1f)
        assertEquals(760f, left.right + 20f - (name.left - 20f), 1f)
        // The values stand under one another, to the right of the name, and begin on one line in every row.
        assertTrue(left.left > name.right)
        assertEquals(left.left, middle.left, 0.5f)
        assertTrue(middle.top >= left.bottom)
        assertEquals(left.left, small.left, 0.5f)
        rule.onNode(hasText(str(R.string.areas_right)) and role(Role.RadioButton)).assertIsSelected()
        // What a press on an area does is said under the name.
        rule.onNode(hasText(str(R.string.look_position_help))).assertExists()
        picture("look-light")
        dark = true
        rule.waitForIdle()
        picture("look-dark")
    }

    @Test @Config(sdk = [37], qualifiers = "w1180dp-h1900dp-mdpi")
    fun everySettingOfLookIsOnThePageAndASwitchIsItsWholeRow() {
        show(BarConfig(items = listOf(ItemConfig("clock", "clock", area = Position.RIGHT)), position = Position.RIGHT, hiddenMode = HiddenMode.CLICK))
        for (id in listOf(R.string.look_items, R.string.barmenu_hidden_items, R.string.look_popups, R.string.look_presenting_live, R.string.look_backup))
            rule.onNode(hasText(str(id)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        for (id in listOf(R.string.look_position, R.string.look_spacing, R.string.look_text_size, R.string.look_color, R.string.look_background,
            R.string.look_collapse, R.string.look_chip, R.string.look_your_settings)) rule.onNode(hasText(str(id))).assertExists()
        // A switch's name and help are the switch: a click on the words turns it.
        val presenting = hasText(str(R.string.look_presenting), substring = true) and role(Role.Switch)
        rule.onNode(presenting).assertIsOff()
        rule.onNode(presenting).performClick()
        rule.waitForIdle()
        rule.onNode(presenting).assertIsOn()
        assertTrue(Store.config.value.presenting)
        picture("look-whole")
        dark = true
        rule.waitForIdle()
        picture("look-whole-dark")
    }

    @Test fun copyAndPasteAnswerInTheirOwnRow() {
        show()
        val copied = hasText(str(R.string.look_copied))
        rule.onNode(copied).assertDoesNotExist()
        rule.onNode(hasText(str(R.string.look_copy))).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(copied).assertExists()
        assertEquals(Store.export(), clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Something that is no layout: the row says so, and nothing changes.
        val layout = Store.config.value
        clipboard.setPrimaryClip(ClipData.newPlainText("", "not a layout"))
        rule.onNode(hasText(str(R.string.look_paste))).performClick()
        rule.waitForIdle()
        rule.onNode(hasText(str(R.string.look_not_settings))).assertExists()
        rule.onNode(copied).assertDoesNotExist()
        assertEquals(layout, Store.config.value)
        picture("look-paste-refused")
    }

    @Test @Config(sdk = [37], qualifiers = "w700dp-h1400dp-mdpi")
    fun inANarrowWindowAChoiceStandsUnderItsName() {
        show()
        val name = bounds(str(R.string.look_position))
        val left = bounds(str(R.string.areas_left))
        assertTrue("the list is under the name", left.top >= name.bottom)
        assertTrue("and begins where the name does", left.left - name.left < 40f)
        val copy = rule.onNode(hasText(str(R.string.look_copy))).fetchSemanticsNode().boundsInRoot
        assertTrue("nothing leaves the window", copy.right <= 700f && rule.onNode(hasText(str(R.string.look_paste))).fetchSemanticsNode().boundsInRoot.right <= 700f)
        picture("look-narrow")
    }
}

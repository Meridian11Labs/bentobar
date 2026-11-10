package com.meridian11.books.bentobar.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import com.meridian11.books.bentobar.ui.BentoButton
import com.meridian11.books.bentobar.ui.ButtonKind
import com.meridian11.books.bentobar.ui.Segment
import com.meridian11.books.bentobar.ui.SegmentPart
import com.meridian11.books.bentobar.ui.SettingsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The settings window's own controls: a segment chooses one part and leaves a part that is off alone; a button that is off takes no click. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1200dp-h800dp-mdpi")
class TokenControlsTest {
    @get:Rule val rule = createComposeRule()

    private fun part(text: String, role: Role) = rule.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, role))

    @Test fun aSegmentChoosesOnePartAndAPartThatIsOffTakesNoClick() {
        var chosen by mutableIntStateOf(0)
        val asked = mutableListOf<Int>()
        rule.setContent {
            SettingsTheme(dark = false) {
                Segment(listOf(SegmentPart("Your bar"), SegmentPart("Add items"), SegmentPart("Select an item", enabled = false)), chosen,
                    onChoose = { asked += it; chosen = it }, role = Role.Tab)
            }
        }
        part("Your bar", Role.Tab).assertIsSelected()
        part("Add items", Role.Tab).assertIsNotSelected().performClick()
        rule.waitForIdle()
        part("Add items", Role.Tab).assertIsSelected()
        part("Your bar", Role.Tab).assertIsNotSelected()
        part("Select an item", Role.Tab).assertIsNotEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf(1), asked)
    }

    @Test fun aSegmentOfValuesIsAGroupOfRadioButtons() {
        rule.setContent { SettingsTheme(dark = true) { Segment(listOf(SegmentPart("Left"), SegmentPart("Middle"), SegmentPart("Right")), chosen = 2, onChoose = {}) } }
        part("Right", Role.RadioButton).assertIsSelected()
        part("Left", Role.RadioButton).assertIsNotSelected()
    }

    @Test fun aButtonTakesAClickUnlessItIsOff() {
        var clicks = 0
        var on by mutableStateOf(true)
        rule.setContent {
            SettingsTheme(dark = false) {
                Column { for (kind in ButtonKind.entries) BentoButton(kind.name, { clicks++ }, kind = kind, enabled = on) }
            }
        }
        for (kind in ButtonKind.entries) part(kind.name, Role.Button).assertIsEnabled().performClick()
        assertEquals(ButtonKind.entries.size, clicks)
        on = false
        rule.waitForIdle()
        for (kind in ButtonKind.entries) part(kind.name, Role.Button).assertIsNotEnabled().performClick()
        assertEquals(ButtonKind.entries.size, clicks)
    }
}

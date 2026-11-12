package com.meridian11.books.bentobar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.meridian11.books.bentobar.bar.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The settings window's palette against the design's contrast table: every pair, in light and dark. Text needs 4.5,
 * a control's outline 3. A changed color changes a figure here, so the table and the palette can't drift apart.
 */
class PaletteContrastTest {
    private class Pair(val name: String, val on: (Palette) -> Color, val ground: (Palette) -> Color, val light: Float, val dark: Float, val min: Float = Contrast.MIN)

    private val table = listOf(
        Pair("ink on panel", { it.ink }, { it.panel }, 17.7f, 14.1f),
        Pair("ink on surface", { it.ink }, { it.surface }, 16.3f, 15.9f),
        Pair("ink on chrome", { it.ink }, { it.chrome }, 15.3f, 14.7f),
        Pair("ink2 on panel", { it.ink2 }, { it.panel }, 8.8f, 9.1f),
        Pair("ink2 on surface", { it.ink2 }, { it.surface }, 8.2f, 10.3f),
        Pair("ink2 on chrome", { it.ink2 }, { it.chrome }, 7.6f, 9.5f),
        Pair("ink2 on sunk", { it.ink2 }, { it.sunk }, 7.7f, 7.9f),
        Pair("ink3 on panel", { it.ink3 }, { it.panel }, 5.9f, 6.4f),
        Pair("ink3 on surface", { it.ink3 }, { it.surface }, 5.5f, 7.3f),
        Pair("ink3 on sunk", { it.ink3 }, { it.sunk }, 5.1f, 5.6f),
        Pair("ink3 on soft", { it.ink3 }, { it.soft }, 4.9f, 5.0f),
        Pair("accent on panel", { it.accent }, { it.panel }, 6.8f, 9.3f),
        Pair("accent on surface", { it.accent }, { it.surface }, 6.3f, 10.5f),
        Pair("accent on soft", { it.accent }, { it.soft }, 5.6f, 7.2f),
        Pair("ink on soft", { it.ink }, { it.soft }, 14.6f, 10.8f),
        Pair("on-primary on primary", { it.onPrimary }, { it.primary }, 11.5f, 9.2f),
        Pair("danger on panel", { it.danger }, { it.panel }, 6.5f, 9.6f),
        Pair("warn ink on warn ground", { it.warnInk }, { it.warnGround }, 7.9f, 9.1f),
        Pair("warn ink on chrome", { it.warnInk }, { it.chrome }, 7.6f, 11.8f),
        Pair("edge on panel", { it.edge }, { it.panel }, 3.6f, 4.1f, min = OUTLINE),
        Pair("edge on surface", { it.edge }, { it.surface }, 3.3f, 4.6f, min = OUTLINE),
        Pair("preview ink on the strip's lightest stop", { it.stripInk }, { p -> p.strip.maxBy { it.luminance() } }, 13.9f, 4.6f),
        // The table's dark figure for this pair, 5.1, is the stop between the two (#5B6F8A): the darkest gives more.
        Pair("preview ink on the strip's darkest stop", { it.stripInk }, { p -> p.strip.minBy { it.luminance() } }, 13.0f, 5.6f),
        Pair("preview ink on teal (off)", { it.stripOffInk }, { it.stripOff }, 10.9f, 10.9f),
    )

    @Test fun everyPairOfTheTableHolds() {
        for (pair in table) for ((theme, palette, figure) in listOf(Triple("light", Palette.Light, pair.light), Triple("dark", Palette.Dark, pair.dark))) {
            val ratio = Contrast.ratio(pair.on(palette), pair.ground(palette))
            assertTrue("${pair.name}, $theme: $ratio is under ${pair.min}", ratio >= pair.min)
            assertTrue("${pair.name}, $theme: $ratio, the table says $figure", abs(ratio - figure) < 0.06f)
        }
    }

    @Test fun thePreviewInkHoldsOnEveryStopOfTheStrip() {
        for ((theme, p) in listOf("light" to Palette.Light, "dark" to Palette.Dark)) for (stop in p.strip)
            assertTrue("preview ink on $stop, $theme", Contrast.ratio(p.stripInk, stop) >= Contrast.MIN)
    }

    @Test fun whatTheControlsAddHoldsToo() {
        for ((theme, p) in listOf("light" to Palette.Light, "dark" to Palette.Dark)) {
            // A segment's raised part carries the ink; the header's "Turn on" and status dot stand on the chrome.
            assertTrue("ink on raised, $theme", Contrast.ratio(p.ink, p.raised) >= Contrast.MIN)
            assertTrue("accent on chrome, $theme", Contrast.ratio(p.accent, p.chrome) >= Contrast.MIN)
            // A switch that is on: the accent track against the ground it sits on, and its thumb against the track.
            assertTrue("accent track on chrome, $theme", Contrast.ratio(p.accent, p.chrome) >= OUTLINE)
            assertTrue("thumb on accent, $theme", Contrast.ratio(p.onPrimary, p.accent) >= OUTLINE)
            assertTrue("thumb on edge, $theme", Contrast.ratio(p.panel, p.edge) >= OUTLINE)
        }
    }

    /**
     * The red button (Remove from the bar, Discard): its words and glyph in the danger colour on the panel, with no
     * ground of its own. Under the pointer and pressed the control's veil is that colour at 6% and 10% over the panel
     * ([states]), and the words must still hold on it; with the keyboard in it the ring is the accent on the panel.
     */
    @Test fun theRedButtonHoldsAtRestUnderThePointerPressedAndWithTheKeyboard() {
        for ((theme, p) in listOf("light" to Palette.Light, "dark" to Palette.Dark)) {
            assertTrue("danger on panel, $theme", Contrast.ratio(p.danger, p.panel) >= Contrast.MIN)
            for ((state, depth) in listOf("hover" to 0.06f, "pressed" to 0.10f)) {
                val ground = p.danger.copy(alpha = depth).compositeOver(p.panel)
                val ratio = Contrast.ratio(p.danger, ground)
                assertTrue("danger on its $state veil, $theme: $ratio", ratio >= Contrast.MIN)
            }
            assertTrue("the focus ring on panel, $theme", Contrast.ratio(p.accent, p.panel) >= OUTLINE)
        }
    }

    @Test fun theSchemeTodaysPagesReadIsThePalette() {
        for (p in listOf(Palette.Light, Palette.Dark)) {
            val s = p.scheme()
            assertEquals(p.surface, s.surface); assertEquals(p.ink, s.onSurface); assertEquals(p.ink2, s.onSurfaceVariant)
            assertEquals(p.panel, s.surfaceContainerLow); assertEquals(p.sunk, s.surfaceContainerHighest)
            assertEquals(p.primary, s.primary); assertEquals(p.onPrimary, s.onPrimary)
            assertEquals(p.soft, s.secondaryContainer); assertEquals(p.ink, s.onSecondaryContainer)
            assertEquals(p.soft, s.primaryContainer); assertEquals(p.ink, s.onPrimaryContainer)
            assertEquals(p.warnGround, s.tertiaryContainer); assertEquals(p.warnInk, s.onTertiaryContainer)
            assertEquals(p.danger, s.error); assertEquals(p.edge, s.outline); assertEquals(p.line, s.outlineVariant)
        }
    }

    private companion object { const val OUTLINE = 3f }
}

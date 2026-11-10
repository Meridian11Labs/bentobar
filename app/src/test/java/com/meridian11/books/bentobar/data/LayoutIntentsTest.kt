package com.meridian11.books.bentobar.data

import com.meridian11.books.bentobar.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** One command places an item one way, whichever surface asks. */
class LayoutIntentsTest {
    private val shown = ItemConfig("s", "clock", Section.SHOWN)
    private val shown2 = ItemConfig("s2", "clock", Section.SHOWN)
    private val hidden = ItemConfig("h", "clock", Section.HIDDEN)
    private val off = ItemConfig("o", "clock", Section.OFF)
    private val layout = BarConfig(items = listOf(shown, shown2, hidden, off))
    private fun BarConfig.ids() = items.map { it.id }
    private fun BarConfig.at(id: String) = items.single { it.id == id }

    @Test fun aCommandChangesAnItemsSectionNeverItsPlaceInTheList() {
        val hid = layout.hide("s")
        assertEquals(Section.HIDDEN, hid.at("s").section)
        assertEquals(layout.ids(), hid.ids())
        val back = layout.show("h")
        assertEquals(Section.SHOWN, back.at("h").section)
        assertEquals(layout.ids(), back.ids())
        val gone = layout.turnOff("s2")
        assertEquals(Section.OFF, gone.at("s2").section)
        assertEquals(layout.ids(), gone.ids())
        val chosen = layout.putIn("o", Section.SHOWN)
        assertEquals(Section.SHOWN, chosen.at("o").section)
        assertEquals(layout.ids(), chosen.ids())
    }

    @Test fun inShowEverythingAHideThatHidesNothingMovesNothingAlongTheStrip() {
        // The default mode draws Hidden items, so an item that jumped to the section's end would jump along the strip.
        val c = BarConfig(items = listOf(shown, hidden.copy(section = Section.SHOWN), shown2))
        assertEquals(HiddenMode.SHOW_ALL, c.hiddenMode)
        val before = c.stripPlan(c.homeArea(), { false }, expanded = false, overflow = false).visible.map { it.id }
        assertEquals(before, c.hide("h").stripPlan(c.homeArea(), { false }, expanded = false, overflow = false).visible.map { it.id })
    }

    @Test fun hideThenShowLeavesAnItemWhereItWas() {
        assertEquals(layout.ids(), layout.hide("s").show("s").ids())
    }

    @Test fun onlyAnExplicitMoveChangesAnItemsPlace() {
        assertEquals(listOf("s2", "s", "h", "o"), layout.copy(items = layout.items.moved("s", Section.HIDDEN, 0)).ids())
    }

    @Test fun aCommandForTheSectionAnItemIsAlreadyInMovesNothing() {
        // The settings' Where chooser asks for the section whichever chip is clicked: the selected one must not reorder.
        assertSame(layout, layout.show("s"))
        assertSame(layout, layout.hide("h"))
        assertSame(layout, layout.turnOff("o"))
    }

    @Test fun anIdThatIsNotInTheLayoutChangesNothing() {
        assertSame(layout, layout.hide("nope"))
        assertEquals(layout, layout.setWhenActive("nope", true))
    }

    @Test fun theRuleBringsAShownOrTurnedOffItemIntoHiddenWhereItIs() {
        // Both the bar's menu and settings do this: where it is, because it pops out at the place it had in the bar.
        val fromShown = layout.setWhenActive("s", true)
        assertEquals(Section.HIDDEN, fromShown.at("s").section)
        assertTrue(fromShown.at("s").whenActive)
        assertEquals(layout.ids(), fromShown.ids())
        // Only hidden items pop out, so a turned-off one comes too (the bar's menu used to leave it Off).
        val fromOff = layout.setWhenActive("o", true)
        assertEquals(Section.HIDDEN, fromOff.at("o").section)
        assertTrue(fromOff.at("o").whenActive)
    }

    @Test fun turningTheRuleOffLeavesAnItemWhereItIs() {
        val c = layout.setWhenActive("h", true).setWhenActive("h", false)
        assertEquals(Section.HIDDEN, c.at("h").section)
        assertEquals(false, c.at("h").whenActive)
        val stayed = layout.copy(items = layout.items.map { if (it.id == "s") it.copy(whenActive = true) else it }).setWhenActive("s", false)
        assertEquals(Section.SHOWN, stayed.at("s").section)
    }

    @Test fun anItemsRuleStaysOnThroughACommandThatChangesItsSection() {
        val rule = layout.setWhenActive("h", true)
        assertTrue(rule.turnOff("h").at("h").whenActive)
        assertTrue(rule.show("h").at("h").whenActive)
    }

    @Test fun puttingAnItemInAnAreaChangesOnlyThatItemsAreaAndNeverItsPlaceInTheList() {
        val moved = layout.putInArea("s2", Position.LEFT)
        assertEquals(Position.LEFT, moved.at("s2").area)
        assertEquals(layout.ids(), moved.ids())
        // Everything else of the layout, and of the item, is as it was.
        assertEquals(layout, moved.copy(items = moved.items.map { if (it.id == "s2") it.copy(area = Position.RIGHT) else it }))
        // An Off item has an area too: it is where the item comes back.
        assertEquals(Position.CENTER, layout.putInArea("o", Position.CENTER).at("o").area)
    }

    @Test fun puttingAnItemInTheAreaItIsInOrAnIdThatIsNotThereChangesNothing() {
        assertSame(layout, layout.putInArea("s", Position.RIGHT))
        assertSame(layout, layout.putInArea("nope", Position.LEFT))
    }

    @Test fun puttingAllInOneAreaChangesEveryItemsAreaOffOnesTooAndNothingElse() {
        val spread = layout.putInArea("s", Position.LEFT).putInArea("h", Position.CENTER)
        val all = spread.putAllIn(Position.LEFT)
        assertEquals(listOf(Position.LEFT, Position.LEFT, Position.LEFT, Position.LEFT), all.items.map { it.area })
        assertEquals(Position.LEFT, all.at("o").area) // the Off one too: it comes back where the others are
        assertEquals(layout.ids(), all.ids())
        assertEquals(layout, all.putAllIn(Position.RIGHT))
        assertSame(all, all.putAllIn(Position.LEFT))
    }

    @Test fun neitherAreaCommandTouchesTheOldPosition() {
        // Only the migration reads it: a version 4 layout's Position is whatever it was when the layout was migrated.
        val old = layout.copy(position = Position.CENTER)
        assertEquals(Position.CENTER, old.putInArea("s", Position.LEFT).position)
        assertEquals(Position.CENTER, old.putAllIn(Position.LEFT).position)
    }

    @Test fun noScreenWritesASectionARuleOrAnAreaOfItsOwn() {
        val handlers = SourceScan.sources.filter { val p = it.path.replace('\\', '/'); "/bentobar/ui/" in p || "/bentobar/bar/" in p }
        assertTrue(handlers.size > 10)
        // `position` with them: it is the area the whole strip had before version 4, and a screen that wrote it would change nothing.
        val byHand = Regex("""\.copy\([^)]*\b(section|whenActive|area|position)\s*=(?!=)|,\s*999\s*\)""")
        assertTrue("the scan sees a write", byHand.containsMatchIn("c.copy(items = c.items.map { it.copy(area = Position.LEFT) })"))
        assertFalse("and not a comparison", byHand.containsMatchIn("it.copy(display = if (it.area == Position.LEFT) a else b)"))
        val found = handlers.filter { byHand.containsMatchIn(SourceScan.code(it)) }.map { it.name }
        assertEquals("a section, a Show when rule or an area is written in a screen: add a Layout intent instead", emptyList<String>(), found)
    }
}

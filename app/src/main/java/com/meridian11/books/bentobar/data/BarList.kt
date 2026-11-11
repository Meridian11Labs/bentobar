package com.meridian11.books.bentobar.data

import com.meridian11.books.bentobar.items.ItemType
import com.meridian11.books.bentobar.items.Items

// How the settings read a layout: what a new item is, which removed items are worth remembering, and the two parts of the
// bar's list. Pure, and nothing here is stored: a remembered setup is an item in Section.OFF, as it always was.

/**
 * What the library's + makes of a type: in the bar, or for a type that speaks up now and then ([ItemType.addsWhenActive])
 * waiting with its rule on. The one definition of a fresh item, for the + and for [carriesSetup].
 */
fun ItemType.fresh(id: String, area: Position): ItemConfig =
    ItemConfig(id, type, if (addsWhenActive) Section.HIDDEN else Section.SHOWN, whenActive = addsWhenActive, options = defaultOptions(), area = area)

/**
 * Whether a removed item is worth remembering: it differs from what the + makes of its type in its options, how it is
 * shown or its rule, or its type keeps something for it outside the layout ([ItemType.keepsOwn]: the flight a Flight
 * item follows). Where it stands (section, area, place) is no setup. An item of a type this version doesn't know is
 * always kept: nothing here can say what it holds.
 */
fun ItemConfig.carriesSetup(type: ItemType? = Items.of(this.type)): Boolean {
    if (type == null) return true
    val fresh = type.fresh(id, area)
    return options != fresh.options || display != fresh.display || whenActive != fresh.whenActive || type.keepsOwn(this)
}

/**
 * The layout without the Off items that carry no setup. Up to 1.8 an item could be turned off as it came; the library
 * lists only setups, so those go with the first change after the update ([Store.update]) and a later change to a type's
 * defaults can't bring them up as setups.
 */
fun BarConfig.withoutBareOff(): BarConfig =
    if (items.none { it.section == Section.OFF && !it.carriesSetup() }) this
    else copy(items = items.filterNot { it.section == Section.OFF && !it.carriesSetup() })

/** The two parts of the bar's list: what is always in the bar, and under the line what waits (for its rule, or behind ‹). */
enum class Part { UPPER, LOWER }

/** One area's rows, in the order they are listed. */
data class Parts(val upper: List<ItemConfig>, val lower: List<ItemConfig>) {
    operator fun get(part: Part): List<ItemConfig> = if (part == Part.UPPER) upper else lower
    val size: Int get() = upper.size + lower.size
}

/**
 * The part [item] is listed in, or null for one that is not in the bar. In Show everything, the default mode, the lower
 * part is "When active": exactly the Hidden items with their rule on. A Hidden item without one is drawn all the time
 * there, so it is listed above. With ‹ the lower part is "Hidden": every Hidden item.
 */
fun BarConfig.partOf(item: ItemConfig): Part? = when (item.section) {
    Section.OFF -> null
    Section.SHOWN -> Part.UPPER
    Section.HIDDEN -> if (hiddenMode == HiddenMode.SHOW_ALL && !item.whenActive) Part.UPPER else Part.LOWER
}

/**
 * The rows of [area], each part in the list's order, which is the strip's left to right; Next meeting first in its part,
 * as the strip draws it ([stripPlan]). Items of a type this version doesn't know are listed too: they are in the layout.
 */
fun BarConfig.parts(area: Position): Parts {
    val (upper, lower) = items.filter { it.area == area && it.section != Section.OFF }
        .sortedBy { if (it.type == Store.PINNED_LEFT) 0 else 1 }
        .partition { partOf(it) == Part.UPPER }
    return Parts(upper, lower)
}

/**
 * The areas the bar's list has rows for, Left before Middle before Right; with no item in the bar, the one a new item
 * goes to. More than one is [usesAreas].
 */
fun BarConfig.listAreas(): List<Position> =
    listOf(Position.LEFT, Position.CENTER, Position.RIGHT).filter { a -> items.any { it.area == a && it.section != Section.OFF } }
        .ifEmpty { listOf(homeArea()) }

/**
 * Whether [item] can be put in the lower part. In the default mode that takes a "Show when…" rule, which not every type
 * has; behind ‹ anything can wait. An item of a type this version doesn't know stays where it is.
 */
fun BarConfig.canGoBelow(item: ItemConfig, type: ItemType? = Items.of(item.type)): Boolean =
    type != null && (hiddenMode != HiddenMode.SHOW_ALL || type.canBeActive)

/** How an item stands in the bar, as Visibility words it in settings and in the bar's own menu. */
enum class Standing {
    /** Always in the bar. */
    ALWAYS,
    /** The default mode's lower part: in the bar only while its "Show when…" rule applies. */
    WHEN_ACTIVE,
    /** Behind ‹ (and popping out by its rule, if that is on). */
    BEHIND,
}

/** How [item] stands, or null for one that is not in the bar: its [part][partOf], in the words of the hidden mode. */
fun BarConfig.standing(item: ItemConfig): Standing? = when (partOf(item)) {
    null -> null
    Part.UPPER -> Standing.ALWAYS
    Part.LOWER -> if (hiddenMode == HiddenMode.SHOW_ALL) Standing.WHEN_ACTIVE else Standing.BEHIND
}

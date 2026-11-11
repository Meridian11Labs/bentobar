package com.meridian11.books.bentobar.data

/**
 * What BentoBar had and has no more: the item types it took out, and the online services only they asked. The one list,
 * read by [readLayout] and [Online.init]. Pure.
 *
 * An item of a type this version doesn't know otherwise stays in the layout (it may be from a newer version) and has a
 * row in Settings that says so. An item of a retired type is from an older one and will never be known again, so a
 * layout that is read drops it, with its options, and the next save writes the layout without it. That needs no new
 * layout version: what is left is a layout of the same schema, and an older version reads it as it reads any other.
 */
object Retired {
    /** Crypto: only in 2.0's test builds. */
    val types: Set<String> = setOf("crypto")

    /** The ids in `Online`'s file of the services only a retired type asked: Crypto's Kraken. */
    val services: Set<String> = setOf("kraken")
}

/** This layout without the items of a [Retired] type (each with its options), or this very layout when it has none. */
fun BarConfig.withoutRetired(): BarConfig =
    if (items.none { it.type in Retired.types }) this else copy(items = items.filterNot { it.type in Retired.types })

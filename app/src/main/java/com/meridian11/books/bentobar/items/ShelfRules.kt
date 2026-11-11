package com.meridian11.books.bentobar.items

/**
 * The shelf's rules, pure: what it takes (text and links, never files), in what order, how many, how an entry is
 * named in its popup, and how its entries are written in the file kept for the item.
 */
object ShelfRules {
    /** The most entries a shelf keeps: the oldest goes when a new one comes. */
    const val MOST = 20

    /** The longest entry it takes, in characters; a longer one is refused whole, never cut. */
    const val LONGEST = 4_000

    enum class Kind { LINK, TEXT }

    data class Entry(val text: String, val kind: Kind) {
        /** For logs and dumps: never the words. */
        override fun toString() = "Entry($kind, ${text.length} characters)"
    }

    /** One item of a dropped or copied clip, as far as the shelf looks at it: its text, and the address it carries, if any. */
    data class Part(val text: String?, val uri: String?) {
        override fun toString() = "Part(text of ${text?.length ?: 0}, uri=${uri != null})"
    }

    private val link = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** A single http(s) address with no spaces is a link; anything else, an address among words included, is text. */
    fun kind(text: String): Kind = if (link.matches(text)) Kind.LINK else Kind.TEXT

    /**
     * [incoming] put on a shelf that holds [held]: each trimmed; blanks, texts over [LONGEST] and what is already held
     * are dropped; the new ones first, in the order they came; [MOST] kept.
     */
    fun take(held: List<Entry>, incoming: List<String>): List<Entry> {
        val fresh = incoming.asSequence().map { it.trim() }.filter { it.isNotEmpty() && it.length <= LONGEST }
            .distinct().filter { t -> held.none { it.text == t } }.map { Entry(it, kind(it)) }.toList()
        return (fresh + held).take(MOST)
    }

    /** [held] without the entry whose text is [text]. */
    fun without(held: List<Entry>, text: String): List<Entry> = held.filterNot { it.text == text }

    /** What the popup's row says of [entry]: its first line with words on it, cut with an ellipsis to [limit] characters. */
    fun line(entry: Entry, limit: Int = 60): String {
        val first = entry.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return TextRules.short(TextRules.oneLine(first, limit + 1), limit)
    }

    /**
     * The texts a dropped clip gives the shelf, in the clip's order: each item's text, or the address it carries. An item
     * that carries a file (an address of the scheme `content` or `file`) is refused whole, its text too: the shelf would
     * have to open the file to know what it is, and a service's window cannot be given the right to.
     */
    fun dropped(parts: List<Part>): List<String> = parts.mapNotNull { p ->
        val scheme = p.uri?.substringBefore(':', "")?.lowercase()
        when {
            scheme == "content" || scheme == "file" -> null
            p.text != null -> p.text
            else -> p.uri
        }
    }

    /**
     * [entries] as the item's kept file holds them: each text after its length and a colon ("5:hello"), one after
     * another. Nothing in a text is escaped, so the file is never longer than the texts and their lengths.
     */
    fun encode(entries: List<Entry>): String = buildString { for (e in entries) append(e.text.length).append(':').append(e.text) }

    /**
     * What a kept file says, read as far as it can be: a part that does not add up ends the reading, and what came before
     * it stays. What it holds is taken as [take] takes anything: a file says no more than a drop could.
     */
    fun decode(kept: String): List<Entry> {
        val texts = ArrayList<String>()
        var i = 0
        while (i < kept.length && texts.size < MOST * 4) {
            val colon = kept.indexOf(':', i)
            if (colon <= i || colon - i > 6) break
            val length = kept.substring(i, colon).toIntOrNull() ?: break
            val end = colon + 1 + length
            if (length < 0 || end > kept.length) break
            texts += kept.substring(colon + 1, end)
            i = end
        }
        return take(emptyList(), texts)
    }
}

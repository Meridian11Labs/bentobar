package com.meridian11.books.bentobar.items

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure rules for Value from a URL: what a reply's value is (its first line, or a JSON path to a number, a text or
 * true/false), how it is shown, and how often it is asked. No Android imports, and no logging: a reply, a value and an
 * address are the user's own business here.
 */
object WebValueRules {
    /** The most of a value the bar shows. */
    const val VALUE_CHARS = 24
    /** The most of a value kept in memory at all: a reply is never kept as it came. */
    const val KEPT_CHARS = 200
    const val LABEL_CHARS = 12
    const val UNIT_CHARS = 8
    const val PATH_CHARS = 120
    /** After being told to slow down, unless the server named a longer time or the item's own pace is longer. */
    const val SLOW_MS = 30 * 60_000L

    /** How often the address is asked, in minutes: what the item's options offer. */
    val EVERY = listOf(1, 5, 15, 60, 1440)
    val every = IntOpt("every", 5, 1..1440)
    val path = Opt.text("path", PATH_CHARS)
    val label = Opt.text("label", LABEL_CHARS)
    val unit = Opt.text("unit", UNIT_CHARS)

    /**
     * What one ask came to: the [value] read from the reply (null: the reply had none) with the [path] it was read by, or
     * what went wrong and the value there was before. Never the reply itself. It prints no value.
     */
    data class Reading(
        val value: String? = null,
        val path: String = "",
        val fetchedAt: Long = 0,
        val failure: Failure? = null,
        /** How many tries in a row went out and failed. */
        val misses: Int = 0,
        /** How long the server asked to be left alone. */
        val retryAfterSec: Long = 0,
    ) {
        override fun toString() = "Reading(value of ${value?.length ?: 0}, failure=$failure)"
    }

    /**
     * The value in [body]: with an empty [path] the body's first line that is not blank (a page of markup is none); else the
     * number, text or true/false that [path] leads to, as names and indexes (`a.b[0].c`, `[2].price`). Anything else, an
     * object, a list, null, a name or an index that is not there, or a body that is not JSON, is null. One line, at most
     * [KEPT_CHARS].
     */
    fun read(body: String, path: String): String? {
        val p = path.trim()
        val found = if (p.isEmpty()) firstLine(body) else try {
            steps(p)?.let { walk(Json.parseToJsonElement(body), it) }
        } catch (e: Exception) {
            null
        } catch (e: StackOverflowError) {
            null
        }
        return found?.let { TextRules.oneLine(it, KEPT_CHARS) }?.takeIf { it.isNotBlank() }
    }

    private fun firstLine(body: String): String? =
        body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.takeIf { !it.startsWith("<") }

    private val STEP = Regex("""([^.\[\]]*)((?:\[\d{1,6}])*)""")
    private val INDEX = Regex("""\[(\d{1,6})]""")

    /** [path] as names (String) and indexes (Int), or null if it is not one. */
    private fun steps(path: String): List<Any>? {
        val out = ArrayList<Any>()
        for (part in path.split('.')) {
            val m = STEP.matchEntire(part) ?: return null
            val name = m.groupValues[1]
            val indexes = INDEX.findAll(m.groupValues[2]).map { it.groupValues[1].toInt() }.toList()
            if (name.isEmpty() && indexes.isEmpty()) return null
            if (name.isNotEmpty()) out.add(name)
            out.addAll(indexes)
        }
        return out
    }

    private fun walk(root: JsonElement, steps: List<Any>): String? {
        var at: JsonElement = root
        for (step in steps) at = when (step) {
            is String -> (at as? JsonObject)?.get(step)
            is Int -> (at as? JsonArray)?.getOrNull(step)
            else -> null
        } ?: return null
        return (at as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    }

    /** "Label 12.5 unit": the label and the unit as the user wrote them, the value cut at [VALUE_CHARS], a number with more than two decimals rounded to two. */
    fun shown(value: String, label: String, unit: String): String =
        listOf(label.trim(), TextRules.first(rounded(value.trim()), VALUE_CHARS), unit.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private val DECIMALS = Regex("""-?\d+\.(\d+)""")

    private fun rounded(value: String): String =
        DECIMALS.matchEntire(value)?.takeIf { it.groupValues[1].length > 2 }?.let { BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString() } ?: value

    /** The item's pace, in milliseconds: [minutes] held to a minute at least and a day at most. */
    fun everyMs(minutes: Int): Long = minutes.coerceIn(every.range) * 60_000L

    /**
     * When [r] is asked again, for an item asked every [minutes]: at its own pace after a value or no value, and after no
     * answer; later after being told to slow down; and after a try that found no connection sooner at first, as the other
     * online items do (without a network nothing goes out), never later than its own pace.
     */
    fun every(r: Reading, minutes: Int): Long {
        val own = everyMs(minutes)
        return if (r.failure == null) own else Pace.after(r.failure, r.misses, r.retryAfterSec, retryMs = own, slowMs = maxOf(own, SLOW_MS))
    }

    /** A staged look: the reading and the host the popup names; no host is no address. */
    class Sample(val reading: Reading?, val host: String?)

    /** Debug samples (`./bento debug webvalue stage value|novalue|offline|nourl`): made up, with a made-up host. */
    fun sample(name: String, now: Long): Sample? = when (name) {
        "value" -> Sample(Reading("21.46", "", now - 2 * 60_000), SAMPLE_HOST)
        "novalue" -> Sample(Reading(null, "", now - 60_000), SAMPLE_HOST)
        "offline" -> Sample(Reading("19.875", "", now - 25 * 60_000, Failure.OFFLINE, misses = 2), SAMPLE_HOST)
        "nourl" -> Sample(null, null)
        else -> null
    }

    private const val SAMPLE_HOST = "sensor.example"
}

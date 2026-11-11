package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.WebValueRules.Reading
import com.meridian11.books.bentobar.net.Http
import com.meridian11.books.bentobar.net.OwnAddress
import com.meridian11.books.bentobar.net.Reply

// Asking an address the user typed, for Value from a URL. The one caller of Http.getOwn. No Android, and no logging: the
// address and the reply are the user's own, and a request is logged in one place (Http.log) as "own".

object WebValueLoad {
    /**
     * Asks [address]; blocks. A good answer is the value [path] finds in it (null where it finds none), and nothing else
     * of the reply is kept. A failure keeps the value there was, with what went wrong. Null: there was nothing to ask, or
     * it was not asked after all (the switch went off, the bar hid).
     */
    fun load(address: OwnAddress?, path: String, last: Reading?, now: Long): Reading? {
        if (address == null) return null
        val reply = Http.getOwn(address)
        if (reply is Reply.Ok) return Reading(WebValueRules.read(reply.text, path), path, now)
        val missed = Failures.missed(reply) ?: return null
        val known = last ?: Reading(path = path)
        return known.copy(failure = missed.failure, misses = known.misses + if (missed.went) 1 else 0, retryAfterSec = missed.waitSec)
    }
}

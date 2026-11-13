package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Kept
import com.meridian11.books.bentobar.data.Online
import com.meridian11.books.bentobar.items.SportsRules.League
import com.meridian11.books.bentobar.items.SportsRules.Scoreboard
import com.meridian11.books.bentobar.net.Host
import com.meridian11.books.bentobar.net.Http
import com.meridian11.books.bentobar.net.Reply
import com.meridian11.books.bentobar.net.Request

/**
 * Fetches scoreboard snapshots from ESPN (`site.api.espn.com`) and persists the last good parsed
 * [Scoreboard] per league so restarts do not re-request fresh data.
 * Pure Kotlin, no Android imports, and no logging.
 */
object SportsLoad {
    private val service = Online.Service.ESPN

    /** The leagues currently followed by Sports items in [items]. */
    fun leagues(items: List<ItemConfig>): Set<League> =
        items.filter { it.type == "sports" }
            .flatMapTo(HashSet()) { SportsRules.leagues(it) }

    /** All team filter tokens across Sports items in [items]; empty if any item follows a whole league unfiltered. */
    fun followedTeamsFor(league: League, items: List<ItemConfig>): List<String> {
        val watching = items.filter { it.type == "sports" && league in SportsRules.leagues(it) }
        if (watching.isEmpty()) return emptyList()
        // If any active item watches this league with no team filter, all games in the league are relevant.
        if (watching.any { SportsRules.teams(it).isEmpty() }) return emptyList()
        return watching.flatMap { SportsRules.teams(it) }.distinct()
    }

    fun load(league: League, last: Scoreboard?, now: Long, activeLeagues: () -> Set<League> = { setOf(league) }): Scoreboard? {
        if (!Online.on(service)) return null
        val reply = Http.get(Request(Host.ESPN, league.path))
        val good = (reply as? Reply.Ok)?.let { SportsRules.read(league, it.text, now, last) }?.also { keep(it, activeLeagues()) }
        if (good != null) return good
        val missed = Failures.missed(reply, slowAfterMs = SportsRules.RETRY_MS) ?: return null
        return failed(league, last, missed.failure, missed.went, missed.waitSec)
    }

    fun failed(league: League, last: Scoreboard?, failure: Failure, went: Boolean, waitSec: Long = 0L): Scoreboard {
        val known = last?.takeIf { it.league == league } ?: Scoreboard(league)
        return known.copy(
            failure = failure,
            misses = known.misses + if (went) 1 else 0,
            retryAfterSec = waitSec,
        )
    }

    private fun keep(board: Scoreboard, active: Set<League>) {
        try {
            val kept = Kept.fetched(service)
            if (board.league in active) kept.write(board.league.code, SportsRules.keep(board), board.fetchedAt)
            kept.keepOnly(active.mapTo(HashSet()) { it.code })
        } catch (e: Exception) {
            // Disk unavailable; next start will fetch fresh.
        }
    }

    fun tidy(active: Set<League>) {
        try {
            Kept.fetched(service).keepOnly(active.mapTo(HashSet()) { it.code })
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun kept(league: League, now: Long): Refresher.Restored<Scoreboard>? = try {
        val kept = Kept.fetched(service)
        val entry = kept.read(league.code)
        val board = entry?.let { SportsRules.kept(it.text) }?.takeIf { it.league == league }
        if (entry != null && board == null) kept.remove(league.code)
        board?.let { Refresher.Restored(it, (now - it.fetchedAt).coerceAtLeast(0L)) }
    } catch (e: Exception) {
        null
    }
}

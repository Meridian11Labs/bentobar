package com.meridian11.books.bentobar.net

import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

/**
 * An address the user typed for Value from a URL: the one road out that does not lead to a [Host]. It is parsed here, in
 * the one file that may name java.net, and is only ever as wide as [parse] lets it be: HTTPS, a host, no name or password,
 * no fragment. It is the user's own text and may hold a token, and its host may be the user's own server, so it prints
 * as "own": only the item's popup shows its [host], and only to the user.
 */
class OwnAddress private constructor(internal val text: String, val host: String) {
    override fun toString() = "own"

    companion object {
        const val MOST_CHARS = 2_000

        /**
         * [text] as an address, or null unless: at most [MOST_CHARS] characters, no whitespace, control or format
         * character (nothing that turns the text around either), the scheme exactly `https`, a host, no user info (no
         * `@` in the authority) and no fragment. A port, a path and a query are the user's own.
         */
        fun parse(text: String): OwnAddress? {
            if (text.isEmpty() || text.length > MOST_CHARS || '#' in text) return null
            if (text.any { it.isWhitespace() || it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }) return null
            val uri = try { URI(text) } catch (e: Exception) { return null }
            if (uri.scheme != "https" || uri.isOpaque || uri.rawFragment != null) return null
            val authority = uri.rawAuthority ?: return null
            if ('@' in authority || uri.rawUserInfo != null) return null
            val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
            return OwnAddress(text, host)
        }
    }
}

/** What carries an [OwnAddress]: [HttpTransport] in the app, a fake in tests. It must not throw. */
fun interface OwnTransport {
    fun get(address: OwnAddress): Reply
}

/**
 * A request over HTTPS with the platform's own HttpURLConnection. The address is put together here
 * and nowhere else: `https://`, the [Host]'s domain, a path of plain characters, the encoded query.
 * A redirect is an answer (a status), never followed, so nothing can lead to another host.
 * An [OwnAddress] goes the same way, as it was typed, and takes at most [Http.OWN_MAX_BYTES].
 *
 * Whatever goes wrong becomes a [Reply.Failed]. No exception leaves this class and none is logged:
 * the platform puts the address into exception messages, and an address can hold a key.
 */
class HttpTransport(
    /** Opens the connection for an address. Only tests pass one (to reach a server on this machine). */
    private val open: (String) -> HttpURLConnection = ::openChecked,
    private val connectMs: Int = Http.CONNECT_MS,
    private val readMs: Int = Http.READ_MS,
    private val maxBytes: Int = Http.MAX_BYTES,
    private val totalMs: Int = Http.TOTAL_MS,
    /** Opens the connection for an [OwnAddress]. Only tests pass one. */
    private val openOwn: (String) -> HttpURLConnection = ::openOwnChecked,
    private val ownMaxBytes: Int = Http.OWN_MAX_BYTES,
) : Transport, OwnTransport {

    override fun get(request: Request): Reply {
        val address = address(request, Http.key(request.host)) ?: return Reply.Failed(Why.REFUSED, sent = false)
        return fetch(address, open, "application/json", maxBytes)
    }

    /** The same request in every way but where it goes, what it accepts (a value may be plain text) and how much of it is taken. */
    override fun get(address: OwnAddress): Reply = fetch(address.text, openOwn, OWN_ACCEPT, ownMaxBytes)

    private fun fetch(address: String, opener: (String) -> HttpURLConnection, accept: String, most: Int): Reply {
        val deadline = System.nanoTime() + totalMs * 1_000_000L
        var connection: HttpURLConnection? = null
        return try {
            val c = opener(address).also { connection = it }
            c.connectTimeout = minOf(connectMs, totalMs)
            c.readTimeout = minOf(readMs, totalMs)
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.requestMethod = "GET"
            // Not the platform's default, which names the device model and the Android build.
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            c.setRequestProperty("Accept", accept)
            // Asked for by name and unpacked below, so Android and the JVM (the unit tests) behave alike.
            c.setRequestProperty("Accept-Encoding", "gzip")
            val status = c.responseCode
            if (status !in 200..299) return Reply.Failed(Why.STATUS, status, retryAfter(c))
            // Connecting and the headers can use up the whole allowance: an answer that took that long is given up too, even an empty one.
            if (System.nanoTime() - deadline > 0) throw SocketTimeoutException()
            val packed = c.contentEncoding.orEmpty().trim().equals("gzip", ignoreCase = true)
            val bytes = c.inputStream.let { if (packed) GZIPInputStream(it) else it }.use { read(it, deadline, most) }
                ?: return Reply.Failed(Why.TOO_LARGE, status)
            Reply.Ok(String(bytes, Charsets.UTF_8))
        } catch (t: Throwable) {
            Reply.Failed(why(t))
        } finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
        }
    }

    /**
     * At most [most] bytes, or null if there is more: a reply that large is nobody's forecast. Each
     * read has its own time limit ([readMs]), and so has the whole: past [deadline] (System.nanoTime)
     * an answer that still trickles in is given up as a timeout, looked at before each read and after it.
     */
    private fun read(stream: InputStream, deadline: Long, most: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            if (System.nanoTime() - deadline > 0) throw SocketTimeoutException()
            val n = stream.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > most) return null
            out.write(buffer, 0, n)
            if (System.nanoTime() - deadline > 0) throw SocketTimeoutException()
        }
    }

    private fun retryAfter(c: HttpURLConnection): Long? =
        c.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it in 0..86_400 }

    companion object {
        private val PATH = Regex("(/[A-Za-z0-9._~-]+)+")

        /** What an own address is asked for: JSON, or plain text, which a value often is. */
        private const val OWN_ACCEPT = "application/json, text/plain;q=0.9"

        /**
         * The address of [request], or null if its path isn't a plain one: it must start with "/"
         * and hold only letters, digits and `. _ ~ -` between slashes (so no `//`, `@`, `?`, `#`,
         * backslash or space), and no segment that is exactly `.` or `..`. Dots anywhere else are
         * nothing: a date range like `/v1/2026-10-05..` is a plain segment too.
         * [key]: the user's own key, added under the name its host takes one by ([Host.keyName]), after the request's own query.
         */
        fun address(request: Request, key: String? = null): String? {
            if (!PATH.matches(request.path) || request.path.split('/').any { it == "." || it == ".." }) return null
            val own = request.query + listOfNotNull(request.host.keyName?.takeIf { !key.isNullOrEmpty() }?.let { it to key!! })
            val query = own.joinToString("&") { (name, value) -> encode(name) + "=" + encode(value) }
            return "https://" + request.host.domain + request.path + if (query.isEmpty()) "" else "?$query"
        }

        private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

        /** The real opener: once more, only HTTPS and only BentoBar's hosts. Nothing is connected yet when it returns. */
        internal fun openChecked(address: String): HttpURLConnection {
            val url = URL(address)
            check(url.protocol == "https" && Host.entries.any { it.domain == url.host }) { "not one of BentoBar's hosts" }
            return url.openConnection() as HttpURLConnection
        }

        /**
         * The real opener of an address the user typed: once more, only what [OwnAddress.parse] takes, and an address the
         * platform reads the same way (HTTPS, the same host, no user info, no fragment). Nothing is connected yet when it
         * returns. Its refusal names no address.
         */
        internal fun openOwnChecked(text: String): HttpURLConnection {
            val own = OwnAddress.parse(text)
            check(own != null) { "not an address BentoBar may ask" }
            val url = URI(text).toURL()
            check(url.protocol == "https" && url.host == own.host && url.userInfo == null && url.ref == null) { "not an address BentoBar may ask" }
            return url.openConnection() as HttpURLConnection
        }

        /** What went wrong, as one of a few kinds. The exception itself is dropped here. */
        internal fun why(t: Throwable): Why = when (t) {
            is SocketTimeoutException -> Why.TIMEOUT
            is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketException, is SSLException -> Why.OFFLINE
            else -> Why.UNREADABLE
        }
    }
}

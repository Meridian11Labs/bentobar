package com.meridian11.books.bentobar.items

/**
 * The Wi-Fi and Addresses sections of the Network menu, and its subtitle: which rows a link gets and what each one
 * says. Unit-tested (`LinkRulesTest`).
 */
object LinkRules {
    /**
     * The active network's link as the reader hands it over, every address as Android's text for it, so that the rules
     * below need no Android: "10.0.2.16", and IPv6 in its short form with a link-local one's zone behind it
     * ("fe80::1c2d:3e4f:5a6b:7c8d%wlan0"). [gateways]: those of its default routes. [dns]: its resolvers.
     * [privateDns]: Private DNS is in use on this link (Android's `isPrivateDnsActive`: in the automatic mode it is not
     * while the network's resolver can't do it); [privateDnsHost]: the host it was set to, null in the automatic mode.
     * Nested here so that it is never taken for the Connection item's `ConnectionRules.Link`.
     */
    data class Link(
        val addresses: List<String> = emptyList(),
        val gateways: List<String> = emptyList(),
        val dns: List<String> = emptyList(),
        val privateDns: Boolean = false,
        val privateDnsHost: String? = null,
    )

    /** The rows, in the menu's order: the Wi-Fi section's, then the Addresses section's. */
    enum class Field { LINK_SPEED, BAND, SIGNAL, IPV4, IPV6, GATEWAY, DNS, PRIVATE_DNS }

    /**
     * The Wi-Fi link as the reader hands it over, in plain numbers: the speed each way in Mbps, the frequency in MHz,
     * and Android's signal level with the top of its scale ([wifi]). -1 is "not known": Android's own value for the
     * speeds and the frequency, and the same here for the level and its scale. [links]: the links of a connection that
     * runs on several at once (Wi-Fi 7's multi-link operation), and empty for any other. With several, Android's
     * speeds, frequency and signal are those of the link with the strongest signal.
     */
    data class Wifi(
        val rxMbps: Int, val txMbps: Int, val frequencyMhz: Int, val level: Int, val maxLevel: Int,
        val links: List<Radio> = emptyList(),
    )

    /** Where one link is: its band, and its channel's number within it. */
    data class Radio(val band: Band, val channel: Int)

    enum class Band { GHZ_2_4, GHZ_5, GHZ_6 }

    enum class Signal { EXCELLENT, GOOD, FAIR, POOR }

    data class Row(val field: Field, val value: String)

    /** The words from the resources: Private DNS's On, and what it says while it is not in use. */
    class Words(val on: String, val off: String)

    /**
     * The Wi-Fi rows' text from the resources, each around its numbers so that the order of the words stays the
     * resource's. Three for the speed, so that which one a link gets is decided here ([speed]) and not in the menu.
     */
    class WifiWords(
        val both: (Int, Int) -> String,
        val down: (Int) -> String,
        val up: (Int) -> String,
        val band: (Band) -> String,
        val bandChannel: (String, Int) -> String,
        val bands: (String, String) -> String,
        val signal: (Signal) -> String,
    )

    /** Android's signal while it knows none (`WifiInfo.INVALID_RSSI`), in dBm. */
    const val NO_SIGNAL = -127

    /** Between the parts of one value, as the menu's subtitle joins its words. */
    const val SEP = " · "

    /**
     * The DNS row's value has room for this many characters beside its label: the widest pair seen to fit on the
     * card, an IPv4 resolver and a shortened IPv6 one. Two shortened IPv6 resolvers are 35, and the label broke into
     * a column of letters. A count of characters, not a measure of the text: a change to the card's width or to the
     * row's type needs a new look at it (`./bento debug network stage wifi`, then `stage ipv6` and `stage vpn`). The
     * Private DNS row keeps to the same room.
     */
    private const val DNS_ROW = 30

    /**
     * The rows for [link]: none for no link, or one without an address yet, and the section is left out. Private DNS
     * says whether this link's lookups are encrypted, so its row is there with any address.
     */
    fun rows(link: Link?, words: Words): List<Row> {
        if (link == null) return emptyList()
        val v4 = ipv4(link.addresses)
        val v6 = ipv6(link.addresses)
        if (v4 == null && v6 == null) return emptyList()
        return listOfNotNull(
            v4?.let { Row(Field.IPV4, it) },
            v6?.let { Row(Field.IPV6, short(it)) },
            gateway(link.gateways)?.let { Row(Field.GATEWAY, short(it)) },
            dns(link.dns)?.let { Row(Field.DNS, it) },
            Row(Field.PRIVATE_DNS, privateDns(link, words)),
        )
    }

    /**
     * The Wi-Fi link from what Android reports. The signal's level is Android's own reading of [rssi], on its own
     * scale: [level] and [maxLevel] ask the Wi-Fi service for them. No signal is not asked about. Where either has
     * no answer, the link has no level: its Signal row is left out and the others stay.
     */
    fun wifi(
        rxMbps: Int, txMbps: Int, frequencyMhz: Int, rssi: Int, links: List<Radio> = emptyList(),
        level: (Int) -> Int?, maxLevel: () -> Int?,
    ): Wifi {
        val unknown = Wifi(rxMbps, txMbps, frequencyMhz, -1, -1, links)
        if (rssi == NO_SIGNAL) return unknown
        val known = level(rssi) ?: return unknown
        val top = maxLevel() ?: return unknown
        return unknown.copy(level = known, maxLevel = top)
    }

    /**
     * The Wi-Fi rows for [wifi]: none for no Wi-Fi link, and the section is left out. A row whose value Android does
     * not know is left out too. A connection on several links has a Band row for each ([radios]).
     */
    fun wifiRows(wifi: Wifi?, words: WifiWords): List<Row> {
        if (wifi == null) return emptyList()
        return listOfNotNull(speed(wifi.rxMbps, wifi.txMbps, words)?.let { Row(Field.LINK_SPEED, it) }) +
            radios(wifi).map { Row(Field.BAND, words.bandChannel(words.band(it.band), it.channel)) } +
            listOfNotNull(signal(wifi.level, wifi.maxLevel)?.let { Row(Field.SIGNAL, words.signal(it)) })
    }

    /**
     * Where the Wi-Fi link is: the band and channel of its frequency, and none for a frequency outside the bands. A
     * connection on several links at once has one for each link, the lower band first, whatever Android's order.
     * One link listed alone says no more than the frequency does, so the frequency is read.
     */
    fun radios(wifi: Wifi): List<Radio> {
        if (wifi.links.size > 1) return wifi.links.distinct().sortedWith(compareBy({ it.band }, { it.channel }))
        val band = band(wifi.frequencyMhz) ?: return emptyList()
        val channel = channel(wifi.frequencyMhz) ?: return emptyList()
        return listOf(Radio(band, channel))
    }

    /** The subtitle's band: each band the link is on, once and the lower first ("5 GHz + 6 GHz"); null where none is known. */
    fun bands(wifi: Wifi?, words: WifiWords): String? =
        wifi?.let(::radios)?.map { it.band }?.distinct()?.map(words.band)?.reduceOrNull(words.bands)

    /**
     * One link of a multi-link connection from Android's numbers for it: the band as `ScanResult.WIFI_BAND_*` has
     * it (1, 2 and 8 for the three bands) and the channel. Null for another band, or for a link without a channel.
     */
    fun radio(band: Int, channel: Int): Radio? {
        val known = when (band) {
            1 -> Band.GHZ_2_4
            2 -> Band.GHZ_5
            8 -> Band.GHZ_6
            else -> return null
        }
        return if (channel > 0) Radio(known, channel) else null
    }

    /** The link's speed down and up on one row; a direction of 0 or less is not known and is left out; null for neither. */
    fun speed(rxMbps: Int, txMbps: Int, words: WifiWords): String? = when {
        rxMbps > 0 && txMbps > 0 -> words.both(rxMbps, txMbps)
        rxMbps > 0 -> words.down(rxMbps)
        txMbps > 0 -> words.up(txMbps)
        else -> null
    }

    /**
     * The band a frequency is in, by the ranges Android's `ScanResult` gives them; null outside the three. 5935 MHz
     * is the 6 GHz band's own, below its other channels.
     */
    fun band(frequencyMhz: Int): Band? = when (frequencyMhz) {
        in 2412..2484 -> Band.GHZ_2_4
        in 5160..5885 -> Band.GHZ_5
        5935, in 5955..7115 -> Band.GHZ_6
        else -> null
    }

    /**
     * The channel's number within its band; null exactly where [band] is. A band's channels are 5 MHz apart, but for
     * two that stand off the steps: 2484 MHz is 14, and 5935 MHz is 2.
     */
    fun channel(frequencyMhz: Int): Int? = when (band(frequencyMhz)) {
        Band.GHZ_2_4 -> if (frequencyMhz == 2484) 14 else (frequencyMhz - 2407) / 5
        Band.GHZ_5 -> (frequencyMhz - 5000) / 5
        Band.GHZ_6 -> if (frequencyMhz == 5935) 2 else (frequencyMhz - 5950) / 5
        null -> null
    }

    /**
     * The signal as a word, by the level's share of Android's scale, whose top differs between devices: the top is
     * Excellent, three quarters and more Good, half and more Fair. On the usual scale of 0 to 4 these are the words
     * Android's Settings show for 4, 3 and 2. Null for no level, or a scale without a top.
     */
    fun signal(level: Int, maxLevel: Int): Signal? {
        if (level < 0 || maxLevel <= 0) return null
        val share = level.toDouble() / maxLevel
        return when {
            share >= 1 -> Signal.EXCELLENT
            share >= 0.75 -> Signal.GOOD
            share >= 0.5 -> Signal.FAIR
            else -> Signal.POOR
        }
    }

    /**
     * The menu's subtitle: the network's kind, the Wi-Fi band behind it, then VPN and metered where they apply.
     * Only Wi-Fi has a band ([onWifi]), whatever is handed over.
     */
    fun subtitle(kind: String, onWifi: Boolean, band: String?, vpn: String?, metered: String?): String =
        listOfNotNull(kind, band.takeIf { onWifi }, vpn, metered).joinToString(SEP)

    /** The first IPv4 address. */
    fun ipv4(addresses: List<String>): String? = addresses.firstOrNull { ':' !in it }

    /**
     * The first IPv6 address that reaches beyond the link: a global one (2000::/3) ahead of a site's own, never the
     * link-local one (fe80::/10) or the loopback. Told apart by the text's first digits, which Android writes in
     * lower case and without a leading zero.
     */
    fun ipv6(addresses: List<String>): String? {
        val beyond = addresses.map(::plain).filter { ':' in it && it != "::1" && it.take(3) !in LINK_LOCAL }
        return beyond.firstOrNull { it[0] == '2' || it[0] == '3' } ?: beyond.firstOrNull()
    }

    /**
     * The gateway: an IPv4 one ahead of an IPv6 one, which is the router's link-local address and tells a reader
     * little; null where the default route has none (a tunnel).
     */
    fun gateway(gateways: List<String>): String? = (gateways.firstOrNull { ':' !in it } ?: gateways.firstOrNull())?.let(::plain)

    /** The DNS row: the first resolver, and the second with it where both fit the row; null for none. */
    fun dns(servers: List<String>): String? {
        val shown = servers.take(2).map { short(plain(it)) }
        if (shown.isEmpty()) return null
        return shown.joinToString(SEP).takeIf { it.length <= DNS_ROW } ?: shown.first()
    }

    /**
     * Private DNS as the row says it: not in use, On (the automatic mode), or On with the host it was set to, as
     * much of it as the row has room for ([host]).
     */
    fun privateDns(link: Link, words: Words): String {
        val host = link.privateDnsHost
        return when {
            !link.privateDns -> words.off
            host.isNullOrBlank() -> words.on
            else -> words.on + SEP + host(host, DNS_ROW - words.on.length - SEP.length)
        }
    }

    /**
     * A host name in [room] characters: whole where it fits, else its last labels behind "…"
     * ("…cloudflare-dns.com"), so that the provider stays readable; a provider's host that names an account
     * ("abc123.dns.nextdns.io") then also loses that part first. A last label longer than the room is cut to its end.
     */
    fun host(name: String, room: Int): String {
        if (name.length <= room) return name
        val labels = name.split('.')
        for (from in 1 until labels.size) {
            val tail = labels.drop(from).joinToString(".")
            if (tail.length + 1 <= room) return "…$tail"
        }
        return "…" + name.takeLast((room - 1).coerceAtLeast(1))
    }

    /** An address without its zone ("%wlan0"). */
    fun plain(address: String): String = address.substringBefore('%')

    /**
     * An address as a row has room for it: one of more than sixteen characters, which only an IPv6 one is (the
     * longest IPv4 one is fifteen), as its first two groups, "…" and its last ("2a02:c7c:…:7f3e"). A group folded
     * into "::" reads 0. What comes out is sixteen characters at most, so every shortened value fits where a whole
     * one does.
     */
    fun short(address: String): String {
        if (address.length <= 16) return address
        // Over sixteen characters there are four groups or more, so the second one is there.
        val groups = address.split(':').map { it.ifEmpty { "0" } }
        return "${groups[0]}:${groups[1]}:…:${groups.last()}"
    }

    /** A line for the debug hook: which rows the Wi-Fi link and the link have, and never a value, an address or a host. */
    fun present(link: Link?, wifi: Wifi?): String {
        val wifiRows = wifiRows(wifi, NO_WORDS).map { it.field.name.lowercase() }
        val linkRows = if (link == null) "no link" else rows(link, Words("", "")).joinToString(" ") {
            if (it.field == Field.PRIVATE_DNS) "private_dns=" + (if (link.privateDns) "on" else "off") else it.field.name.lowercase()
        }.ifEmpty { "no address" }
        return (wifiRows + linkRows).joinToString(" ")
    }

    /** For [present], which asks which Wi-Fi rows there are and not what they say. */
    private val NO_WORDS = WifiWords({ _, _ -> "" }, { "" }, { "" }, { "" }, { _, _ -> "" }, { _, _ -> "" }, { "" })

    /** How the text of a link-local address (fe80::/10, so fe80 to febf) starts. */
    private val LINK_LOCAL = setOf("fe8", "fe9", "fea", "feb")
}

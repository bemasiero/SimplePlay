package com.simpleplay.tv

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Streaming XMLTV parser. Only keeps programmes for the given channels and only
 * those that are on now or in the next 24 hours, so huge EPG files stay cheap.
 * Channels are matched by tvg-id first, then by display name.
 */
object EpgParser {
    private const val HOUR = 3_600_000L

    fun parse(
        input: InputStream,
        channels: List<Channel>,
        now: Long = System.currentTimeMillis(),
    ): Map<String, List<Programme>> {
        val byId = HashMap<String, MutableList<String>>()
        val byName = HashMap<String, MutableList<String>>()
        for (c in channels) {
            if (c.tvgId.isNotBlank()) byId.getOrPut(c.tvgId.lowercase()) { ArrayList() } += c.key
            byName.getOrPut(norm(c.name)) { ArrayList() } += c.key
        }

        val idMap = HashMap<String, List<String>>() // xmltv channel id -> our channel keys
        val out = HashMap<String, MutableList<Programme>>()
        val horizon = now + 24 * HOUR

        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        var channelId: String? = null
        try {
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "channel" -> {
                            val id = p.getAttributeValue(null, "id").orEmpty()
                            channelId = id
                            byId[id.lowercase()]?.let { idMap[id] = it }
                        }
                        "display-name" -> {
                            val id = channelId
                            if (id != null && id !in idMap) {
                                byName[norm(p.nextText())]?.let { idMap[id] = it }
                            }
                        }
                        "programme" -> {
                            val id = p.getAttributeValue(null, "channel").orEmpty()
                            val keys = idMap[id] ?: byId[id.lowercase()]
                            if (keys != null) {
                                val start = time(p.getAttributeValue(null, "start"))
                                val stop = time(p.getAttributeValue(null, "stop")).takeIf { it > 0 } ?: Long.MAX_VALUE
                                if (start > 0 && stop > now && start < horizon) {
                                    val prog = readProgramme(p, start, stop)
                                    for (k in keys) out.getOrPut(k) { ArrayList() } += prog
                                }
                            }
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && p.name == "channel") {
                    channelId = null
                }
                ev = p.next()
            }
        } catch (e: Exception) {
            // Broken or truncated file: keep whatever we managed to read.
        }

        for (list in out.values) {
            list.sortBy { it.start }
            // Programmes without a stop time end when the next one starts.
            for (i in list.indices) {
                if (list[i].stop == Long.MAX_VALUE) {
                    val end = list.getOrNull(i + 1)?.start ?: (list[i].start + HOUR)
                    list[i] = list[i].copy(stop = end)
                }
            }
        }
        return out
    }

    /** Returns (on now, up next). */
    fun nowNext(list: List<Programme>?, now: Long): Pair<Programme?, Programme?> {
        if (list.isNullOrEmpty()) return null to null
        val i = list.indexOfFirst { it.stop > now }
        if (i < 0) return null to null
        val cur = list[i]
        return if (cur.start <= now) cur to list.getOrNull(i + 1) else null to cur
    }

    private fun readProgramme(p: XmlPullParser, start: Long, stop: Long): Programme {
        var title = ""
        var desc = ""
        val depth = p.depth
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && p.depth == depth) break
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "title" -> if (title.isEmpty()) title = p.nextText().trim()
                    "desc" -> if (desc.isEmpty()) desc = p.nextText().trim()
                }
            }
        }
        return Programme(start, stop, title, desc)
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val utc = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** XMLTV time, e.g. "20261031200000 +0100". */
    @Synchronized
    fun time(s: String?): Long {
        val t = s?.trim() ?: return 0
        val digits = t.takeWhile { it.isDigit() }
        if (digits.length < 12) return 0
        val stamp = digits.take(14).padEnd(14, '0')
        val zone = t.substring(digits.length).trim()
        return try {
            if (zone.isNotEmpty()) withZone.parse("$stamp $zone")!!.time else utc.parse(stamp)!!.time
        } catch (e: Exception) {
            0
        }
    }
}

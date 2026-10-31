package com.simpleplay.tv

import java.io.BufferedReader
import java.io.File
import java.io.Reader
import java.io.StringReader

/**
 * Minimal M3U / M3U8 (extended) playlist parser. Reads line by line so huge
 * playlists never have to fit in memory, and drops VOD (movies / series)
 * so only live channels are kept.
 */
object M3u {
    class Result(val channels: List<Channel>, val epgUrl: String)

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val vodExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "flv", "wmv", "webm", "mpg", "mpeg", "3gp")

    fun parse(text: String, skipVod: Boolean = true): Result = parse(StringReader(text), skipVod)

    fun parse(reader: Reader, skipVod: Boolean = true): Result {
        val channels = ArrayList<Channel>()
        var epgUrl = ""
        var name = "" // the pending #EXTINF line
        var extGroup = ""

        val buffered = if (reader is BufferedReader) reader else BufferedReader(reader, 65_536)
        while (true) {
            val raw = buffered.readLine() ?: break
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U") -> {
                    val a = attrs(line)
                    epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: "").split(',').first().trim()
                }
                line.startsWith("#EXTINF") -> {
                    name = line // parsed lazily below, only for live entries
                    extGroup = ""
                }
                line.startsWith("#EXTGRP:") -> extGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit // #EXTVLCOPT etc.
                else -> {
                    if (!skipVod || !isVod(line)) {
                        val extinf = name
                        val a = if (extinf.isEmpty()) emptyMap() else attrs(extinf)
                        val title = titleOf(extinf)
                            .ifBlank { a["tvg-name"].orEmpty() }
                            .ifBlank { line.substringAfterLast('/') }
                        channels += Channel(
                            name = title,
                            url = line,
                            group = a["group-title"].orEmpty().ifBlank { extGroup },
                            tvgId = a["tvg-id"].orEmpty(),
                            logo = a["tvg-logo"].orEmpty(),
                        )
                    }
                    name = ""
                    extGroup = ""
                }
            }
        }
        return Result(channels, epgUrl)
    }

    /** Xtream VOD lives under /movie/ and /series/; plain file extensions are VOD too. */
    fun isVod(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        if ("/movie/" in path || "/series/" in path) return true
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        return ext in vodExtensions
    }

    /** Writes a compact playlist (used as the on-device cache). */
    fun write(file: File, channels: List<Channel>, epgUrl: String) {
        file.bufferedWriter().use { w ->
            w.write(if (epgUrl.isBlank()) "#EXTM3U\n" else "#EXTM3U url-tvg=\"${clean(epgUrl)}\"\n")
            for (c in channels) {
                w.write("#EXTINF:-1 tvg-id=\"${clean(c.tvgId)}\" tvg-logo=\"${clean(c.logo)}\" group-title=\"${clean(c.group)}\",${c.name}\n")
                w.write(c.url)
                w.write("\n")
            }
        }
    }

    private fun clean(s: String) = s.replace("\"", "'")

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

    /** The channel name is everything after the first comma that is not inside quotes. */
    private fun titleOf(line: String): String {
        var quoted = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return line.substring(i + 1).trim()
            }
        }
        return ""
    }
}

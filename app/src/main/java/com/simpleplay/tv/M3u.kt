package com.simpleplay.tv

/** Minimal M3U / M3U8 (extended) playlist parser. */
object M3u {
    class Result(val channels: List<Channel>, val epgUrl: String)

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(text: String): Result {
        val channels = ArrayList<Channel>()
        var epgUrl = ""
        var info: Map<String, String>? = null
        var name = ""
        var extGroup = ""

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U") -> {
                    val a = attrs(line)
                    epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: "").split(',').first().trim()
                }
                line.startsWith("#EXTINF") -> {
                    info = attrs(line)
                    name = titleOf(line)
                    extGroup = ""
                }
                line.startsWith("#EXTGRP:") -> extGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit // #EXTVLCOPT etc.
                else -> {
                    val a = info ?: emptyMap()
                    val title = name
                        .ifBlank { a["tvg-name"].orEmpty() }
                        .ifBlank { line.substringAfterLast('/') }
                    channels += Channel(
                        name = title,
                        url = line,
                        group = a["group-title"].orEmpty().ifBlank { extGroup },
                        tvgId = a["tvg-id"].orEmpty(),
                        logo = a["tvg-logo"].orEmpty(),
                    )
                    info = null
                    name = ""
                    extGroup = ""
                }
            }
        }
        return Result(channels, epgUrl)
    }

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

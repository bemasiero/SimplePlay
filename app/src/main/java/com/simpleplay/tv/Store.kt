package com.simpleplay.tv

import android.content.Context
import java.io.File
import java.io.IOException

/** Settings + cached playlist. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("simpleplay", Context.MODE_PRIVATE)
    private val playlistFile = File(context.filesDir, "playlist.m3u")

    var playlistUrl: String
        get() = prefs.getString("playlist_url", "")!!
        set(v) = prefs.edit().putString("playlist_url", v).apply()

    /** User supplied EPG url (optional). */
    var epgUrl: String
        get() = prefs.getString("epg_url", "")!!
        set(v) = prefs.edit().putString("epg_url", v).apply()

    /** EPG url advertised in the playlist header (url-tvg). */
    private var playlistEpgUrl: String
        get() = prefs.getString("playlist_epg_url", "")!!
        set(v) = prefs.edit().putString("playlist_epg_url", v).apply()

    // Xtream Codes login (optional alternative to a playlist URL).
    var xtreamServer: String
        get() = prefs.getString("xtream_server", "")!!
        set(v) = prefs.edit().putString("xtream_server", v).apply()

    var xtreamUser: String
        get() = prefs.getString("xtream_user", "")!!
        set(v) = prefs.edit().putString("xtream_user", v).apply()

    var xtreamPass: String
        get() = prefs.getString("xtream_pass", "")!!
        set(v) = prefs.edit().putString("xtream_pass", v).apply()

    val usesXtream: Boolean get() = xtreamServer.isNotBlank()

    val effectiveEpgUrl: String
        get() = epgUrl
            .ifBlank { if (usesXtream) Xtream.epgUrl(xtreamServer, xtreamUser, xtreamPass) else "" }
            .ifBlank { playlistEpgUrl }

    var selected: Set<String>
        get() = prefs.getStringSet("selected", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("selected", HashSet(v)).apply()

    var lastChannel: String
        get() = prefs.getString("last_channel", "")!!
        set(v) = prefs.edit().putString("last_channel", v).apply()

    val playlistAgeMs: Long get() = System.currentTimeMillis() - playlistFile.lastModified()

    /**
     * Streams the playlist to disk, then parses it line by line keeping only live
     * channels, and caches that (much smaller) list. Never holds the raw file in memory.
     */
    fun downloadPlaylist(): List<Channel> {
        val raw = File(playlistFile.parentFile, "playlist.download")
        try {
            Http.gunzipIfNeeded(Http.openRaw(playlistUrl)).use { input ->
                raw.outputStream().use { input.copyTo(it, 65_536) }
            }
            val result = raw.bufferedReader().use { M3u.parse(it) }
            if (result.channels.isEmpty()) throw IOException("No live channels found. Is this an M3U playlist?")
            M3u.write(playlistFile, result.channels, result.epgUrl)
            playlistEpgUrl = result.epgUrl
            return result.channels
        } finally {
            raw.delete()
        }
    }

    fun cachedPlaylist(): List<Channel>? =
        if (playlistFile.exists()) playlistFile.bufferedReader().use { M3u.parse(it).channels } else null

    fun myChannels(all: List<Channel>): List<Channel> {
        val sel = selected
        return all.filter { it.key in sel }.distinctBy { it.key }
    }
}

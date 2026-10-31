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

    val effectiveEpgUrl: String get() = epgUrl.ifBlank { playlistEpgUrl }

    var selected: Set<String>
        get() = prefs.getStringSet("selected", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("selected", HashSet(v)).apply()

    var lastChannel: String
        get() = prefs.getString("last_channel", "")!!
        set(v) = prefs.edit().putString("last_channel", v).apply()

    val playlistAgeMs: Long get() = System.currentTimeMillis() - playlistFile.lastModified()

    fun downloadPlaylist(): List<Channel> {
        val text = Http.readText(playlistUrl)
        val result = M3u.parse(text)
        if (result.channels.isEmpty()) throw IOException("No channels found. Is this an M3U playlist?")
        playlistFile.writeText(text)
        playlistEpgUrl = result.epgUrl
        return result.channels
    }

    fun cachedPlaylist(): List<Channel>? =
        if (playlistFile.exists()) M3u.parse(playlistFile.readText()).channels else null

    fun myChannels(all: List<Channel>): List<Channel> {
        val sel = selected
        return all.filter { it.key in sel }.distinctBy { it.key }
    }
}

package com.simpleplay.tv

import android.content.Context
import java.io.File

/** Downloads (with a 12h cache) and holds EPG data for the picked channels. */
object Epg {
    private const val MAX_AGE = 12 * 3_600_000L

    @Volatile
    var data: Map<String, List<Programme>> = emptyMap()
        private set

    fun nowNext(channel: Channel) = EpgParser.nowNext(data[channel.key], System.currentTimeMillis())

    fun load(context: Context, url: String, channels: List<Channel>) {
        if (url.isBlank()) return
        val file = File(context.cacheDir, "epg.cache")
        val prefs = context.getSharedPreferences("epg", Context.MODE_PRIVATE)
        val fresh = file.exists() &&
            prefs.getString("url", "") == url &&
            System.currentTimeMillis() - file.lastModified() < MAX_AGE

        if (!fresh) {
            try {
                val tmp = File(context.cacheDir, "epg.tmp")
                Http.openRaw(url).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                file.delete()
                tmp.renameTo(file)
                prefs.edit().putString("url", url).apply()
            } catch (e: Exception) {
                // Fall back to an older copy if we have one for the same URL.
                if (!file.exists() || prefs.getString("url", "") != url) throw e
            }
        }
        data = Http.gunzipIfNeeded(file.inputStream()).use { EpgParser.parse(it, channels) }
    }
}

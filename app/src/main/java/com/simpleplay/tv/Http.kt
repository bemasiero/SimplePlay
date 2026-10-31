package com.simpleplay.tv

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Http {
    const val USER_AGENT = "SimplePlay/1.0 (Android TV)"

    /** Opens a URL, following redirects (including http <-> https). */
    fun openRaw(url: String): InputStream {
        if (!url.startsWith("http", ignoreCase = true)) return URL(url).openStream()
        var current = url
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", USER_AGENT)
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location") ?: throw IOException("Redirect without location")
                c.disconnect()
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IOException("Server answered HTTP $code")
            }
            return c.inputStream
        }
        throw IOException("Too many redirects")
    }

    /** Transparently un-gzips .gz files (common for XMLTV). */
    fun gunzipIfNeeded(input: InputStream): InputStream {
        val b = BufferedInputStream(input, 65_536)
        b.mark(2)
        val first = b.read()
        val second = b.read()
        b.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(b, 65_536) else b
    }

    fun readText(url: String): String =
        gunzipIfNeeded(openRaw(url)).use { it.readBytes().toString(Charsets.UTF_8) }
}

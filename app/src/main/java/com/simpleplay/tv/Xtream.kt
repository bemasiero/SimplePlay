package com.simpleplay.tv

import java.net.URLEncoder

/** Xtream Codes panels expose the whole lineup as an M3U plus an XMLTV guide. */
object Xtream {
    /** "provider.tv:8080/" -> "http://provider.tv:8080" */
    fun normalizeServer(server: String): String {
        var s = server.trim().trimEnd('/')
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        return s
    }

    fun playlistUrl(server: String, user: String, pass: String) =
        "${normalizeServer(server)}/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts"

    fun epgUrl(server: String, user: String, pass: String) =
        "${normalizeServer(server)}/xmltv.php?username=${enc(user)}&password=${enc(pass)}"

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")
}

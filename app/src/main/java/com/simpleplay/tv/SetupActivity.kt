package com.simpleplay.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlin.concurrent.thread

/** Step 1: a playlist URL or an Xtream Codes login (+ optional EPG URL). */
class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        val store = Store(this)
        val playlist = findViewById<EditText>(R.id.playlist_url)
        val server = findViewById<EditText>(R.id.xtream_server)
        val user = findViewById<EditText>(R.id.xtream_user)
        val pass = findViewById<EditText>(R.id.xtream_pass)
        val epg = findViewById<EditText>(R.id.epg_url)
        val next = findViewById<Button>(R.id.next)
        val status = findViewById<TextView>(R.id.status)

        if (!store.usesXtream) playlist.setText(store.playlistUrl)
        server.setText(store.xtreamServer)
        user.setText(store.xtreamUser)
        pass.setText(store.xtreamPass)
        epg.setText(store.epgUrl)
        if (store.playlistUrl.isNotBlank()) next.requestFocus()

        next.setOnClickListener {
            val m3u = playlist.text.toString().trim()
            val srv = server.text.toString().trim()
            val usr = user.text.toString().trim()
            val pwd = pass.text.toString().trim()
            when {
                srv.isNotEmpty() && (usr.isEmpty() || pwd.isEmpty()) -> {
                    status.setText(R.string.xtream_incomplete)
                    return@setOnClickListener
                }
                srv.isNotEmpty() -> {
                    store.xtreamServer = srv
                    store.xtreamUser = usr
                    store.xtreamPass = pwd
                    store.playlistUrl = Xtream.playlistUrl(srv, usr, pwd)
                }
                m3u.isNotEmpty() -> {
                    store.xtreamServer = ""
                    store.xtreamUser = ""
                    store.xtreamPass = ""
                    store.playlistUrl = m3u
                }
                else -> {
                    status.setText(R.string.enter_url)
                    return@setOnClickListener
                }
            }
            store.epgUrl = epg.text.toString().trim()
            status.setText(R.string.downloading)
            next.isEnabled = false
            thread {
                val result = runCatching { store.downloadPlaylist() }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    next.isEnabled = true
                    result
                        .onSuccess {
                            status.text = getString(R.string.found_channels, it.size)
                            startActivity(Intent(this, ChannelsActivity::class.java))
                        }
                        .onFailure { status.text = getString(R.string.load_failed, it.message ?: it.javaClass.simpleName) }
                }
            }
        }
    }
}

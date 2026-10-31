package com.simpleplay.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlin.concurrent.thread

/** Step 1: playlist URL (+ optional EPG URL). */
class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        val store = Store(this)
        val playlist = findViewById<EditText>(R.id.playlist_url)
        val epg = findViewById<EditText>(R.id.epg_url)
        val next = findViewById<Button>(R.id.next)
        val status = findViewById<TextView>(R.id.status)

        playlist.setText(store.playlistUrl)
        epg.setText(store.epgUrl)
        if (store.playlistUrl.isNotBlank()) next.requestFocus()

        next.setOnClickListener {
            val url = playlist.text.toString().trim()
            if (url.isEmpty()) {
                status.setText(R.string.enter_url)
                return@setOnClickListener
            }
            store.playlistUrl = url
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

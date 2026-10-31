package com.simpleplay.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.load
import java.util.Date
import kotlin.concurrent.thread

/**
 * Step 3: watch.
 *   UP / DOWN (or CH+/CH-) : zap
 *   OK                     : show info (press again for channel list)
 *   LEFT                   : channel list
 *   MENU                   : settings
 *   BACK                   : hide overlay / exit
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private lateinit var store: Store
    private lateinit var playerView: PlayerView
    private lateinit var message: TextView
    private lateinit var banner: View
    private lateinit var bannerLogo: ImageView
    private lateinit var bannerTitle: TextView
    private lateinit var bannerClock: TextView
    private lateinit var bannerStatus: TextView
    private lateinit var bannerNow: TextView
    private lateinit var bannerProgress: ProgressBar
    private lateinit var bannerDesc: TextView
    private lateinit var bannerNext: TextView
    private lateinit var listPanel: View
    private lateinit var listView: ListView

    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()
    private var index = 0
    private val handler = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable { banner.visibility = View.GONE }
    private val listAdapter = ChannelListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        if (store.playlistUrl.isBlank()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player)
        message = findViewById(R.id.message)
        banner = findViewById(R.id.banner)
        bannerLogo = findViewById(R.id.banner_logo)
        bannerTitle = findViewById(R.id.banner_title)
        bannerClock = findViewById(R.id.banner_clock)
        bannerStatus = findViewById(R.id.banner_status)
        bannerNow = findViewById(R.id.banner_now)
        bannerProgress = findViewById(R.id.banner_progress)
        bannerDesc = findViewById(R.id.banner_desc)
        bannerNext = findViewById(R.id.banner_next)
        listPanel = findViewById(R.id.list_panel)
        listView = findViewById(R.id.channel_list)

        listView.adapter = listAdapter
        listView.setOnItemClickListener { _, _, position, _ ->
            hideList()
            if (position != index) {
                index = position
                play()
            } else {
                showBanner()
            }
        }
        findViewById<Button>(R.id.settings).setOnClickListener { openSettings() }

        loadChannels()
    }

    override fun onStart() {
        super.onStart()
        if (player == null) createPlayer()
        if (channels.isNotEmpty()) play()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        playerView.player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ---- data -------------------------------------------------------------

    private fun loadChannels() {
        message.setText(R.string.loading)
        message.visibility = View.VISIBLE
        thread {
            val result = runCatching { store.cachedPlaylist() ?: store.downloadPlaylist() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result
                    .onSuccess { setChannels(store.myChannels(it)) }
                    .onFailure { message.text = getString(R.string.load_failed, it.message ?: it.javaClass.simpleName) }
            }
            // Quietly refresh an old cached playlist (stream URLs/tokens may change).
            if (result.isSuccess && store.playlistAgeMs > 6 * 3_600_000L) {
                runCatching { store.downloadPlaylist() }.onSuccess { all ->
                    runOnUiThread { if (!isDestroyed) setChannels(store.myChannels(all)) }
                }
            }
        }
    }

    private fun setChannels(list: List<Channel>) {
        val firstLoad = channels.isEmpty()
        val currentKey = channels.getOrNull(index)?.key ?: store.lastChannel
        channels = list
        index = list.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        listAdapter.notifyDataSetChanged()
        if (list.isEmpty()) {
            message.setText(R.string.no_channels)
            message.visibility = View.VISIBLE
            return
        }
        message.visibility = View.GONE
        if (firstLoad) {
            play()
            loadEpg()
        }
    }

    private fun loadEpg() {
        val url = store.effectiveEpgUrl
        if (url.isBlank()) return
        val list = channels
        thread {
            runCatching { Epg.load(applicationContext, url, list) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                listAdapter.notifyDataSetChanged()
                if (banner.visibility == View.VISIBLE) updateBanner()
            }
        }
    }

    // ---- playback ---------------------------------------------------------

    private fun createPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    p.seekToDefaultPosition()
                    p.prepare()
                    return
                }
                bannerStatus.text = getString(R.string.cant_play, error.errorCodeName)
                bannerStatus.visibility = View.VISIBLE
                showBanner(sticky = true)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) bannerStatus.visibility = View.GONE
            }
        })
        playerView.player = p
        player = p
    }

    private fun play() {
        val ch = channels.getOrNull(index) ?: return
        store.lastChannel = ch.key
        bannerStatus.visibility = View.GONE
        showBanner()
        val p = player ?: return
        val item = MediaItem.Builder().setUri(ch.url).apply {
            if (ch.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
    }

    private fun zap(delta: Int) {
        if (channels.isEmpty()) return
        index = (index + delta + channels.size) % channels.size
        play()
    }

    // ---- remote keys ------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val code = event.keyCode

        if (listPanel.visibility == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                hideList()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> zap(+1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
                channels.isEmpty() -> openSettings()
                banner.visibility == View.VISIBLE -> showList()
                else -> showBanner()
            }
            KeyEvent.KEYCODE_INFO -> showBanner()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_GUIDE -> showList()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> openSettings()
            KeyEvent.KEYCODE_BACK -> {
                if (banner.visibility != View.VISIBLE) return super.dispatchKeyEvent(event)
                handler.removeCallbacks(hideBanner)
                banner.visibility = View.GONE
            }
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    private fun openSettings() {
        startActivity(Intent(this, SetupActivity::class.java))
    }

    // ---- overlays ---------------------------------------------------------

    private fun showBanner(sticky: Boolean = false) {
        if (channels.isEmpty()) return
        updateBanner()
        banner.visibility = View.VISIBLE
        handler.removeCallbacks(hideBanner)
        if (!sticky) handler.postDelayed(hideBanner, 5_000)
    }

    private fun updateBanner() {
        val ch = channels.getOrNull(index) ?: return
        val now = System.currentTimeMillis()
        bannerTitle.text = getString(R.string.channel_title, index + 1, ch.name)
        bannerLogo.visibility = if (ch.logo.isBlank()) View.GONE else View.VISIBLE
        bannerLogo.load(ch.logo.ifBlank { null })
        bannerClock.text = hm(now)
        val (cur, next) = Epg.nowNext(ch)
        if (cur != null) {
            bannerNow.text = getString(R.string.now_line, hm(cur.start), hm(cur.stop), cur.title)
            bannerProgress.progress = ((now - cur.start) * 100 / (cur.stop - cur.start).coerceAtLeast(1)).toInt()
            bannerProgress.visibility = View.VISIBLE
            bannerDesc.text = cur.desc
            bannerDesc.visibility = if (cur.desc.isBlank()) View.GONE else View.VISIBLE
        } else {
            bannerNow.setText(R.string.no_epg)
            bannerProgress.visibility = View.GONE
            bannerDesc.visibility = View.GONE
        }
        if (next != null) {
            bannerNext.text = getString(R.string.next_line, hm(next.start), next.title)
            bannerNext.visibility = View.VISIBLE
        } else {
            bannerNext.visibility = View.GONE
        }
    }

    private fun showList() {
        if (channels.isEmpty()) return
        handler.removeCallbacks(hideBanner)
        banner.visibility = View.GONE
        listAdapter.notifyDataSetChanged()
        listPanel.visibility = View.VISIBLE
        listView.requestFocus()
        listView.setSelection(index)
    }

    private fun hideList() {
        listPanel.visibility = View.GONE
    }

    private fun hm(t: Long): String = DateFormat.getTimeFormat(this).format(Date(t))

    private inner class ChannelListAdapter : BaseAdapter() {
        override fun getCount() = channels.size
        override fun getItem(position: Int) = channels[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.row_channel, parent, false)
            val ch = channels[position]
            val current = position == index
            view.findViewById<TextView>(R.id.number).text = (position + 1).toString()
            view.findViewById<ImageView>(R.id.logo).load(ch.logo.ifBlank { null })
            view.findViewById<TextView>(R.id.name).apply {
                text = ch.name
                setTextColor(if (current) 0xFF5EA8FF.toInt() else 0xFFFFFFFF.toInt())
            }
            val nowTitle = Epg.nowNext(ch).first?.title.orEmpty()
            view.findViewById<TextView>(R.id.now).apply {
                text = nowTitle
                visibility = if (nowTitle.isEmpty()) View.GONE else View.VISIBLE
            }
            return view
        }
    }
}

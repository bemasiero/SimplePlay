package com.simpleplay.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Step 2: tick the channels you want. */
class ChannelsActivity : Activity() {
    private lateinit var all: List<Channel>
    private val checked = HashSet<String>()
    private lateinit var adapter: PickAdapter
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channels)
        val store = Store(this)
        all = store.cachedPlaylist().orEmpty()
        if (all.isEmpty()) {
            finish()
            return
        }
        val keys = all.mapTo(HashSet()) { it.key }
        checked += store.selected.filter { it in keys }

        count = findViewById(R.id.count)
        adapter = PickAdapter()
        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, view, position, _ ->
            val key = adapter.getItem(position).key
            if (!checked.remove(key)) checked += key
            (view as CheckedTextView).isChecked = key in checked
            updateCount()
        }

        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = adapter.filter(s?.toString().orEmpty())
        })
        findViewById<Button>(R.id.select_all).setOnClickListener {
            adapter.shown.forEach { checked += it.key }
            adapter.notifyDataSetChanged()
            updateCount()
        }
        findViewById<Button>(R.id.select_none).setOnClickListener {
            adapter.shown.forEach { checked -= it.key }
            adapter.notifyDataSetChanged()
            updateCount()
        }
        findViewById<Button>(R.id.done).setOnClickListener {
            if (checked.isEmpty()) {
                Toast.makeText(this, R.string.pick_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            store.selected = checked
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
        }
        updateCount()
        list.requestFocus()
    }

    private fun updateCount() {
        count.text = getString(R.string.selected_count, checked.size, all.size)
    }

    private inner class PickAdapter : BaseAdapter() {
        var shown: List<Channel> = all
            private set

        fun filter(query: String) {
            shown = if (query.isBlank()) all else all.filter {
                it.name.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true)
            }
            notifyDataSetChanged()
        }

        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.row_pick, parent, false)) as CheckedTextView
            val ch = shown[position]
            view.text = if (ch.group.isBlank()) ch.name else "${ch.name}   ·   ${ch.group}"
            view.isChecked = ch.key in checked
            return view
        }
    }
}

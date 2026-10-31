package com.simpleplay.tv

data class Channel(
    val name: String,
    val url: String,
    val group: String = "",
    val tvgId: String = "",
    val logo: String = "",
) {
    /** Stable id used to remember the user's picks (survives URL token changes). */
    val key: String get() = "$group|$name"
}

data class Programme(val start: Long, val stop: Long, val title: String, val desc: String)

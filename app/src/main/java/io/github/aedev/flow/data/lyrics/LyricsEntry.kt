package io.github.aedev.flow.data.lyrics

data class LyricsEntry(
    val text: String,
    val startTime: Long = 0L,
    val endTime: Long = 0L
)

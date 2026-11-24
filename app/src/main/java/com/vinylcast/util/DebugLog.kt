package com.vinylcast.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val MAX_LINES = 120
    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logText = MutableStateFlow("")
    val logText: StateFlow<String> = _logText.asStateFlow()

    @Synchronized
    fun log(tag: String, message: String) {
        val ts = timeFormat.format(Date())
        val entry = "[$ts][$tag] $message"
        if (lines.size >= MAX_LINES) {
            lines.removeFirst()
        }
        lines.addLast(entry)
        _logText.value = lines.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        lines.clear()
        _logText.value = ""
    }
}

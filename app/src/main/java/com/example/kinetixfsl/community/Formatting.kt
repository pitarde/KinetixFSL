package com.example.kinetixfsl.community

import com.google.firebase.Timestamp
import java.util.concurrent.TimeUnit

/** Compact "9h", "1d", "3w" style — the reddit-style timestamps in the feed. */
internal fun Timestamp?.relativeToNow(nowMillis: Long = System.currentTimeMillis()): String {
    if (this == null) return ""
    val diffMs = (nowMillis - toDate().time).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 30 -> "${days / 7}w"
        days < 365 -> "${days / 30}mo"
        else -> "${days / 365}y"
    }
}

/** "1.1k", "23k", "1.2M" — compact vote/view counts. */
internal fun Long.compact(): String = when {
    this < 1_000 -> toString()
    this < 10_000 -> {
        val v = this / 1000.0
        // Drop the ".0" when the number is exact (e.g. 2000 -> "2k", not "2.0k").
        String.format("%.1f", v).removeSuffix(".0") + "k"
    }
    this < 1_000_000 -> "${this / 1000}k"
    else -> {
        val v = this / 1_000_000.0
        String.format("%.1f", v).removeSuffix(".0") + "M"
    }
}
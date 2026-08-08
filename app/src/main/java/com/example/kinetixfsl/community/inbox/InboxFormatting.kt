package com.example.kinetixfsl.community.inbox

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Timestamps for the inbox.
 *
 * Deliberately not the feed's `relativeToNow()`, which is built for post ages
 * and answers in bare units ("3d", "2w"). A messenger reads differently: recent
 * things want "5m ago", yesterday wants the word, and anything older wants a
 * date the user can actually place. Same information, different question.
 */
internal fun Timestamp?.inboxTime(nowMillis: Long = System.currentTimeMillis()): String {
    if (this == null) return ""
    val then = toDate().time
    val diff = (nowMillis - then).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 && isSameDay(then, nowMillis) -> "${hours}h ago"
        isYesterday(then, nowMillis) -> "Yesterday"
        // Inside the last week the weekday is the most useful label — "Tue"
        // places a message better than "5d" does.
        TimeUnit.MILLISECONDS.toDays(diff) < 7 -> format(then, "EEE")
        isSameYear(then, nowMillis) -> format(then, "MMM d")
        else -> format(then, "MMM d, yyyy")
    }
}

/** "10:42 AM" — the time under a message bubble and on a day separator. */
internal fun Timestamp?.clockTime(): String =
    if (this == null) "" else format(toDate().time, "h:mm a")

/**
 * "Today" / "Yesterday" / "March 3" — the divider between days in a thread, so
 * a long conversation doesn't read as one undifferentiated wall.
 */
internal fun Timestamp?.dayLabel(nowMillis: Long = System.currentTimeMillis()): String {
    if (this == null) return ""
    val then = toDate().time
    return when {
        isSameDay(then, nowMillis) -> "Today"
        isYesterday(then, nowMillis) -> "Yesterday"
        isSameYear(then, nowMillis) -> format(then, "MMMM d")
        else -> format(then, "MMMM d, yyyy")
    }
}

private fun format(millis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(java.util.Date(millis))

private fun calendarOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = calendarOf(a)
    val cb = calendarOf(b)
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(a: Long, now: Long): Boolean {
    val yesterday = calendarOf(now).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return isSameDay(a, yesterday.timeInMillis)
}

private fun isSameYear(a: Long, b: Long): Boolean =
    calendarOf(a).get(Calendar.YEAR) == calendarOf(b).get(Calendar.YEAR)

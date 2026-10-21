package io.github.usmandurrany.tether.demo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.usmandurrany.tether.demo.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Days between the local dates of two moments: 0 for the same day, 1 for yesterday. */
fun daysBetween(earlier: Long, later: Long): Int {
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    // Rounded, so a daylight saving change (a 23 or 25 hour day) still counts as one.
    return Math.round((startOfDay(later) - startOfDay(earlier)) / 86_400_000.0).toInt()
}

fun timeText(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** The chat list's time: the time today, "Yesterday", the weekday this week, else the date. */
@Composable
fun listTime(millis: Long, now: Long = System.currentTimeMillis()): String = when (val days = daysBetween(millis, now)) {
    0 -> timeText(millis)
    1 -> stringResource(R.string.yesterday)
    in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis))
    else -> if (days < 0) timeText(millis) else DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))
}

/** The label of a day separator in a chat. */
@Composable
fun dayText(millis: Long, now: Long = System.currentTimeMillis()): String = when (daysBetween(millis, now)) {
    0 -> stringResource(R.string.today)
    1 -> stringResource(R.string.yesterday)
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

/** "last seen" wording, as before the redesign. */
@Composable
fun lastSeenText(at: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - at).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.last_seen_just_now)
        minutes < 60 -> stringResource(R.string.last_seen_minutes, minutes.toInt())
        minutes < 20 * 60 -> stringResource(R.string.last_seen_at, timeText(at))
        else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))
    }
}

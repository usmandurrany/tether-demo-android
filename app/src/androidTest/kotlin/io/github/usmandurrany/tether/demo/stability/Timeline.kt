package io.github.usmandurrany.tether.demo.stability

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

fun now(): Long = SystemClock.elapsedRealtime()

/**
 * What happened during a test, from three sources on one clock: the SDK's
 * connection state, Android's default network, and whether the app was in
 * the foreground. Printed when a check fails, so a failure shows the order of
 * events that led to it.
 */
class Timeline(private val start: Long = now()) {
    data class Event(val at: Long, val source: String, val text: String)

    val events = CopyOnWriteArrayList<Event>()

    fun add(source: String, text: String) {
        val event = Event(now(), source, text)
        events += event
        Log.i(TAG, "${seconds(event.at)} $source: $text")
    }

    fun since(at: Long, source: String? = null): List<Event> =
        events.filter { it.at >= at && (source == null || it.source == source) }

    /** The last [max] events, oldest first, as lines like "+12.345s sdk: Online(...)". */
    fun describe(max: Int = 80): String =
        events.toList().takeLast(max).joinToString("\n") { "  ${seconds(it.at)} ${it.source}: ${it.text}" }

    private fun seconds(at: Long) = "+%.3fs".format((at - start) / 1000.0)

    private companion object {
        const val TAG = "Stability"
    }
}

/**
 * Measured recovery times, printed at the end of each test with the tag
 * "StabilityReport" so CI can collect them from logcat.
 */
class Report(private val test: String) {
    private data class Row(val scenario: String, val millis: Long?, val limit: Long, val note: String)

    private val rows = CopyOnWriteArrayList<Row>()

    fun record(scenario: String, millis: Long?, limit: Long, note: String = "") {
        rows += Row(scenario, millis, limit, note)
        Log.i(TAG, line(rows.last()))
    }

    fun print() {
        if (rows.isEmpty()) return
        val measured = rows.mapNotNull { it.millis }
        Log.i(
            TAG,
            "$test summary: ${rows.size} checks, ${rows.count { it.millis == null || it.millis > it.limit }} over the limit" +
                if (measured.isEmpty()) "" else ", median ${measured.sorted()[measured.size / 2]} ms, max ${measured.max()} ms",
        )
    }

    private fun line(row: Row): String {
        val verdict = when {
            row.millis == null -> "FAIL (never)"
            row.millis > row.limit -> "FAIL"
            else -> "ok"
        }
        return "$test | ${row.scenario} | ${row.millis ?: "-"} ms | limit ${row.limit} ms | $verdict${if (row.note.isEmpty()) "" else " | ${row.note}"}"
    }

    private companion object {
        const val TAG = "StabilityReport"
    }
}

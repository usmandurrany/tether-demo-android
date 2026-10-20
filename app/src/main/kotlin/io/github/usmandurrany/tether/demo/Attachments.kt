package io.github.usmandurrany.tether.demo

import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.allData

/**
 * The demo's custom message types, all built on the SDK's [MessageData]: a
 * type name plus keys and values, sent as separate tags in the message and
 * read back with `message.data("photo")`. The caption travels as the normal
 * body, so other XMPP apps still show something sensible.
 *
 * Nothing here is special to the SDK: any app can invent its own types the
 * same way, and the "Custom data" option in the chat sends arbitrary ones.
 */
object Attachments {
    const val PHOTO = "photo"
    const val VIDEO = "video"
    const val LOCATION = "location"

    /** A photo hosted somewhere, with what the receiver needs to lay it out before it loads. */
    fun photo(url: String, width: Int?, height: Int?, bytes: Long?, mime: String?): MessageData =
        MessageData(PHOTO, "url" to url, "width" to width, "height" to height, "size" to bytes, "mime" to mime)

    fun video(url: String, seconds: Int?, bytes: Long?): MessageData =
        MessageData(VIDEO, "url" to url, "duration" to seconds, "size" to bytes)

    fun location(latitude: Double, longitude: Double, label: String?): MessageData =
        MessageData(LOCATION, "lat" to latitude, "lon" to longitude, "label" to label)

    /** The text other apps show for a message with only data in it. */
    fun fallbackText(data: MessageData): String = when (data.type) {
        PHOTO, VIDEO -> data["url"].orEmpty()
        LOCATION -> "https://www.openstreetmap.org/?mlat=${data["lat"]}&mlon=${data["lon"]}"
        else -> ""
    }

    /** What a received (or sent) message carries, for drawing it. */
    sealed interface Content
    data class Photo(val url: String, val width: Int?, val height: Int?, val bytes: Long?) : Content
    data class Video(val url: String, val seconds: Int?, val bytes: Long?) : Content
    data class Location(val latitude: Double, val longitude: Double, val label: String?) : Content {
        val mapUrl: String get() = "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=16/$latitude/$longitude"
    }

    /** Any other type: shown as its keys and values, to prove nothing is lost on the way. */
    data class Other(val data: MessageData) : Content

    fun of(message: Message): List<Content> = message.allData().map { d ->
        when (d.type) {
            PHOTO -> d["url"]?.let { Photo(it, d.int("width"), d.int("height"), d.long("size")) }
            VIDEO -> d["url"]?.let { Video(it, d.int("duration"), d.long("size")) }
            LOCATION -> {
                val lat = d.double("lat")
                val lon = d.double("lon")
                if (lat != null && lon != null) Location(lat, lon, d["label"]) else null
            }
            else -> null
        } ?: Other(d)
    }

    fun size(bytes: Long?): String? = bytes?.let {
        when {
            it >= 1_000_000 -> String.format("%.1f MB", it / 1_000_000.0)
            it >= 1_000 -> "${it / 1_000} KB"
            else -> "$it B"
        }
    }

    fun duration(seconds: Int?): String? = seconds?.let { String.format("%d:%02d", it / 60, it % 60) }
}

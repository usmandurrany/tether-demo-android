package io.github.usmandurrany.tether.demo.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Just enough media handling for the demo, with no image library: download a
 * photo once and keep it in memory, and read a link's size and dimensions to
 * send with it. A real app knows those from the file it uploaded.
 */
object MediaProbe {
    class PhotoInfo(val width: Int?, val height: Int?, val bytes: Long?, val mime: String?)

    private const val MAX_BYTES = 20_000_000
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private suspend fun download(url: String): Pair<ByteArray, String?>? = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(url, "GET")
            try {
                c.inputStream.use { it.readUpTo(MAX_BYTES) } to c.contentType?.substringBefore(';')?.trim()
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    suspend fun photo(url: String): PhotoInfo? {
        val (bytes, mime) = download(url) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return PhotoInfo(bounds.outWidth.takeIf { it > 0 }, bounds.outHeight.takeIf { it > 0 }, bytes.size.toLong(), mime)
    }

    suspend fun size(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(url, "HEAD")
            try {
                c.contentLengthLong.takeIf { it > 0 }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    /** The photo at [url], scaled down to at most [maxSide] pixels; cached for the life of the app. */
    suspend fun bitmap(url: String, maxSide: Int = 1024): Bitmap? {
        cache.get(url)?.let { return it }
        val (bytes, _) = download(url) ?: return null
        return withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }?.also { cache.put(url, it) }
    }

    /** InputStream.readNBytes needs Android 13; this works on every version. */
    private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
        }
}

/**
 * A photo from a link. The sender's width and height give the box its shape
 * before the photo arrives, so the chat does not jump as it loads.
 */
@Composable
fun RemotePhoto(url: String, width: Int?, height: Int?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val loaded = MediaProbe.bitmap(url)
        if (loaded == null) failed = true else bitmap = loaded
    }
    val ratio = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else 4f / 3f
    val shape = modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2.5f)).clip(RoundedCornerShape(10.dp))
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = shape)
    } else {
        Box(shape.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Text(if (failed) "Photo couldn't load" else "Loading photo…", style = MaterialTheme.typography.labelSmall)
        }
    }
}


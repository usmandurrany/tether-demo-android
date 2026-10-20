package io.github.usmandurrany.tether.demo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.usmandurrany.tether.im.Avatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Decodes profile pictures once, and turns a picked image into a small JPEG. */
object Avatars {
    private val decoded = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun bitmap(avatar: Avatar): Bitmap? =
        decoded.get(avatar.hash) ?: BitmapFactory.decodeByteArray(avatar.data, 0, avatar.data.size)?.also { decoded.put(avatar.hash, it) }

    /**
     * Reads a picked image, crops it square and makes it a JPEG of at most
     * [size] pixels: small enough to travel in one stanza.
     */
    suspend fun fromUri(context: Context, uri: Uri, size: Int = 192): Avatar? = withContext(Dispatchers.IO) {
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, minOf(size, side), minOf(size, side), true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        Avatar("image/jpeg", out.toByteArray())
    }
}

/**
 * A round profile picture, or the name's initial on a colour picked from the
 * name when there is none, with an optional presence [dot].
 */
@Composable
fun AvatarImage(avatar: Avatar?, name: String, size: Dp = 40.dp, dot: Color? = null, modifier: Modifier = Modifier) {
    val bitmap = remember(avatar?.hash) { avatar?.let(Avatars::bitmap) }
    Box(modifier.size(size)) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Picture of $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape).testTag("avatar-picture"),
            )
        } else {
            val hue = (name.lowercase().hashCode() and 0xffff) / 65535f * 360f
            Box(
                Modifier.size(size).clip(CircleShape).background(Color.hsv(hue, 0.45f, 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp,
                )
            }
        }
        if (dot != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 4 + 2.dp)
                    .clip(CircleShape)
                    .background(dot)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

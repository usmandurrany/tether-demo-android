@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.usmandurrany.tether.demo.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.usmandurrany.tether.demo.Attachments
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.ui.components.Hint
import io.github.usmandurrany.tether.demo.ui.components.MediaProbe
import io.github.usmandurrany.tether.demo.ui.components.SheetAction
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.MessageStatus
import kotlinx.coroutines.launch

internal const val CUSTOM = "custom"

private class AttachType(val type: String, val label: Int, val title: Int, val icon: ImageVector, val color: Color)

private val attachTypes = listOf(
    AttachType(Attachments.PHOTO, R.string.attach_photo, R.string.send_photo, TetherIcons.Photo, Color(0xFF4F46E5)),
    AttachType(Attachments.VIDEO, R.string.attach_video, R.string.send_video, TetherIcons.Video, Color(0xFFBE185D)),
    AttachType(Attachments.LOCATION, R.string.attach_location, R.string.share_location, TetherIcons.Location, Color(0xFF047857)),
    AttachType(CUSTOM, R.string.attach_custom, R.string.send_custom, TetherIcons.Code, Color(0xFFB45309)),
)

/** The paperclip's sheet: photo, video, location or any custom data, WhatsApp style. */
@Composable
internal fun AttachMenuSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.send), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                attachTypes.forEach { item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onPick(item.type) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(shape = CircleShape, color = item.color, modifier = Modifier.size(60.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(item.icon, contentDescription = null, tint = Color.White) }
                        }
                        Text(stringResource(item.label), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * The details of a photo, video, location or custom message. The demo has no
 * file hosting, so photos and videos are links to files already online; the
 * demo reads their size from the web server before sending.
 */
@Composable
internal fun AttachSheet(type: String, onDismiss: () -> Unit, onSend: (String, MessageData) -> Unit) {
    val scope = rememberCoroutineScope()
    val item = attachTypes.first { it.type == type }
    var url by rememberSaveable {
        mutableStateOf(
            when (type) {
                Attachments.PHOTO -> "https://picsum.photos/id/1015/800/533.jpg"
                Attachments.VIDEO -> "https://download.samplelib.com/mp4/sample-5s.mp4"
                else -> ""
            },
        )
    }
    var seconds by rememberSaveable { mutableStateOf("5") }
    var lat by rememberSaveable { mutableStateOf("51.50135") }
    var lon by rememberSaveable { mutableStateOf("-0.14189") }
    var label by rememberSaveable { mutableStateOf("Buckingham Palace") }
    var customType by rememberSaveable { mutableStateOf("contact-card") }
    var fields by rememberSaveable { mutableStateOf("name=Ada Lovelace\nphone=+44 20 7946 0000") }
    var caption by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val linkError = stringResource(R.string.error_link)
    val coordinatesError = stringResource(R.string.error_coordinates)

    fun submit() {
        error = null
        when (type) {
            Attachments.PHOTO, Attachments.VIDEO -> {
                val link = url.trim()
                if (!link.startsWith("https://") && !link.startsWith("http://")) {
                    error = linkError
                    return
                }
                busy = true
                scope.launch {
                    val data = if (type == Attachments.PHOTO) {
                        val info = MediaProbe.photo(link)
                        Attachments.photo(link, info?.width, info?.height, info?.bytes, info?.mime)
                    } else {
                        Attachments.video(link, seconds.trim().toIntOrNull(), MediaProbe.size(link))
                    }
                    busy = false
                    onSend(caption.trim(), data)
                }
            }
            Attachments.LOCATION -> {
                val la = lat.trim().toDoubleOrNull()
                val lo = lon.trim().toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    error = coordinatesError
                    return
                }
                onSend(caption.trim(), Attachments.location(la, lo, label.trim().ifEmpty { null }))
            }
            else -> {
                val pairs = fields.lines().mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
                }
                runCatching { MessageData(customType.trim(), pairs.toMap()) }
                    .onSuccess { onSend(caption.trim(), it) }
                    .onFailure { error = it.message }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = item.color, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(item.icon, contentDescription = null, tint = Color.White) }
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(item.title), style = MaterialTheme.typography.headlineSmall)
            }
            val fieldShape = RoundedCornerShape(12.dp)
            when (type) {
                Attachments.PHOTO, Attachments.VIDEO -> {
                    OutlinedTextField(
                        url,
                        { url = it },
                        label = { Text(stringResource(R.string.field_link)) },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth().testTag("attach-url"),
                    )
                    if (type == Attachments.VIDEO) {
                        OutlinedTextField(
                            seconds,
                            { seconds = it },
                            label = { Text(stringResource(R.string.field_seconds)) },
                            singleLine = true,
                            shape = fieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Attachments.LOCATION -> {
                    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(lat, { lat = it }, label = { Text(stringResource(R.string.field_latitude)) }, singleLine = true, shape = fieldShape, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                        OutlinedTextField(lon, { lon = it }, label = { Text(stringResource(R.string.field_longitude)) }, singleLine = true, shape = fieldShape, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.field_place)) }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    OutlinedTextField(customType, { customType = it }, label = { Text(stringResource(R.string.field_type)) }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(fields, { fields = it }, label = { Text(stringResource(R.string.field_fields)) }, minLines = 3, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                }
            }
            OutlinedTextField(
                caption,
                { caption = it },
                label = { Text(stringResource(R.string.field_caption)) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth().testTag("attach-caption"),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(enabled = !busy, onClick = { submit() }, modifier = Modifier.testTag("attach-send")) {
                    Text(stringResource(if (busy) R.string.reading else R.string.send))
                }
            }
        }
    }
}

/** Long-press on a message: copy it, delete it for everyone (ours only) or just here. */
@Composable
internal fun MessageActionsSheet(message: Message, onDismiss: () -> Unit, onDeleteForEveryone: () -> Unit, onDeleteForMe: () -> Unit) {
    @Suppress("DEPRECATION") // LocalClipboard needs a newer Compose than the demo's BOM.
    val clipboard = LocalClipboardManager.current
    val danger = MaterialTheme.colorScheme.error
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 20.dp)) {
            if (!message.deleted && message.body.isNotBlank()) {
                SheetAction(TetherIcons.Copy, stringResource(R.string.copy_text), onClick = {
                    clipboard.setText(AnnotatedString(message.body))
                    onDismiss()
                })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            Text(
                stringResource(if (message.deleted) R.string.remove_placeholder_title else R.string.delete_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
            Hint(
                stringResource(if (message.outgoing && !message.deleted) R.string.delete_explain_ours else R.string.delete_explain_device),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
            )
            if (message.outgoing && !message.deleted && message.status != MessageStatus.FAILED) {
                SheetAction(TetherIcons.Delete, stringResource(R.string.delete_for_everyone), onClick = onDeleteForEveryone, color = danger, modifier = Modifier.testTag("delete-everyone"))
            }
            SheetAction(
                TetherIcons.Delete,
                stringResource(if (message.deleted) R.string.remove else R.string.delete_for_me),
                onClick = onDeleteForMe,
                color = danger,
                modifier = Modifier.testTag("delete-me"),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.cancel)) }
        }
    }
}

package io.github.usmandurrany.tether.demo.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.debug.Direction as LogDirection
import io.github.usmandurrany.tether.debug.LogEntry
import io.github.usmandurrany.tether.debug.LogLevel
import io.github.usmandurrany.tether.debug.RemoteLogStats
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.demo.ui.home.key
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.demo.ui.theme.chatColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogUiState(
    val entries: List<LogEntry> = emptyList(),
    val stanzas: Boolean = true,
    val follow: Boolean = true,
    /** The log portal's address and upload counts, when one is set. */
    val portal: String? = null,
    val portalStats: RemoteLogStats? = null,
)

/**
 * What tether-debug recorded: every stanza in and out (credentials always
 * redacted) and connection events. Also in Logcat under the tag "Tether".
 */
class LogViewModel(private val session: TetherSession, private val handle: SavedStateHandle) : ViewModel() {
    private val stanzas = handle.getStateFlow("stanzas", true)
    private val follow = handle.getStateFlow("follow", true)
    private val remote = session.remoteLogs

    val state: StateFlow<LogUiState> = combine(
        session.logs.entries,
        stanzas,
        follow,
        remote?.stats ?: flowOf(null),
    ) { entries, stanzas, follow, stats ->
        LogUiState(
            entries = if (stanzas) entries else entries.filter { !it.isStanza },
            stanzas = stanzas,
            follow = follow,
            portal = remote?.config?.endpoint,
            portalStats = stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    fun toggleStanzas() {
        handle["stanzas"] = !stanzas.value
    }

    fun toggleFollow() {
        handle["follow"] = !follow.value
    }

    fun clear() = session.logs.clear()
}

@Composable
fun LogRoute(session: TetherSession) {
    val viewModel = viewModel(key = session.key("log")) { LogViewModel(session, createSavedStateHandle()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LogScreen(state, onToggleStanzas = viewModel::toggleStanzas, onToggleFollow = viewModel::toggleFollow, onClear = viewModel::clear)
}

@Composable
fun LogScreen(state: LogUiState, onToggleStanzas: () -> Unit = {}, onToggleFollow: () -> Unit = {}, onClear: () -> Unit = {}) {
    val list = rememberLazyListState()
    val shown = state.entries
    LaunchedEffect(shown.size, state.follow) {
        if (state.follow && shown.isNotEmpty()) list.scrollToItem(shown.lastIndex)
    }
    val colors = chatColors

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.stanzas,
                onClick = onToggleStanzas,
                label = { Text(stringResource(R.string.log_stanzas)) },
                leadingIcon = { if (state.stanzas) Icon(TetherIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = state.follow,
                onClick = onToggleFollow,
                label = { Text(stringResource(if (state.follow) R.string.log_following else R.string.log_follow)) },
                leadingIcon = { Icon(TetherIcons.Follow, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = false,
                onClick = onClear,
                label = { Text(stringResource(R.string.log_clear)) },
                leadingIcon = { Icon(TetherIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        val portal = state.portal
        val stats = state.portalStats
        if (portal != null && stats != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Icon(TetherIcons.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.log_portal, portal, stats.uploaded.toInt(), stats.queued, stats.dropped.toInt()) +
                            (stats.lastError?.let { stringResource(R.string.log_portal_error, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().background(colors.console),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(shown, key = { it.seq }, contentType = { "line" }) { LogLine(it) }
        }
    }
}

private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

@Composable
private fun LogLine(entry: LogEntry) {
    val colors = chatColors
    val arrow = when (entry.direction) {
        LogDirection.SENT -> "→"
        LogDirection.RECEIVED -> "←"
        null -> entry.tag + ":"
    }
    val color = when {
        entry.level >= LogLevel.ERROR -> colors.consoleError
        entry.direction == LogDirection.SENT -> colors.consoleSent
        entry.direction == LogDirection.RECEIVED -> colors.consoleReceived
        else -> colors.onConsole
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.consoleMuted)) { append(timeFormat.get()!!.format(Date(entry.timestampMillis))) }
            append(" $arrow ${entry.text}")
            entry.error?.let {
                append("\n")
                append(it)
            }
        },
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

package io.github.usmandurrany.tether.demo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.im.Avatar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The top bar and the bottom bar's badges. */
data class HomeUiState(
    val account: String,
    val connection: ConnectionState = ConnectionState.Idle,
    /** Our own name and picture, from our profile, for the top bar's avatar. */
    val myName: String = account,
    val myAvatar: Avatar? = null,
    val unreadTotal: Int = 0,
    val requests: Int = 0,
)

class HomeViewModel(private val session: TetherSession) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        session.client.state,
        session.chat.myProfile,
        session.chat.conversations,
        session.subscriptionRequests,
    ) { connection, profile, conversations, requests ->
        HomeUiState(
            account = session.account.toString(),
            connection = connection,
            myName = profile?.nickname ?: profile?.fullName ?: session.account.toString(),
            myAvatar = profile?.avatar,
            unreadTotal = conversations.sumOf { it.unreadCount },
            requests = requests.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(session.account.toString()))

    /** After a fatal error the SDK stops retrying; only an explicit connect restarts it. */
    fun retry() {
        viewModelScope.launch { session.connect() }
    }
}

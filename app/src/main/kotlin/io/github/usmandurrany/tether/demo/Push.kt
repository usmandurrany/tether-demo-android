package io.github.usmandurrany.tether.demo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.im.MessageStatus
import io.github.usmandurrany.tether.android.PushWakeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** FCM token and notification permission helpers for MongooseIM push. */
object DemoPush {
    fun available(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun token(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result) else cont.resumeWithException(task.exception ?: IllegalStateException("No FCM token"))
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** What the user chose on the Me tab. Plain preferences: nothing secret is in them. */
class PushSettings(context: Context) {
    private val prefs = context.getSharedPreferences("push_settings", Context.MODE_PRIVATE)

    /** The MongooseIM virtual PubSub service JID, such as `pubsub.example.com`. */
    var server: String
        get() = prefs.getString("server", "").orEmpty()
        set(value) = prefs.edit().putString("server", value.trim()).apply()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()
}

/** Receives FCM messages and token changes. Declared in the manifest. */
class DemoPushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        PushWakeService.start(applicationContext, message.priority == RemoteMessage.PRIORITY_HIGH,
            message.data["last-message-sender"], message.data["last-message-body"])
    }

    override fun onNewToken(token: String) {
        (application as DemoApp).pushTokenChanged(token)
    }
}

/**
 * Shows a notification for each new message that arrives while the app is in
 * the background, and clears them when it comes back. Messages that were
 * already unread when the session started are not announced again.
 */
class MessageNotifier(private val context: Context, private val session: TetherSession, scope: CoroutineScope) {
    private val announced = HashSet<String>()
    private val lifecycle = ProcessLifecycleOwner.get().lifecycle

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_HIGH))
        scope.launch {
            // Messages kept on the device load as the session starts; nothing can arrive
            // from the server before the first connection attempt, so everything there by
            // then is old news.
            session.client.state.first { it is ConnectionState.Connecting || it is ConnectionState.Online }
            session.chat.allMessages.value.values.flatten().forEach { announced += it.id }
            session.chat.conversations.collect { conversations ->
                val background = !lifecycle.currentStateFlow.value.isAtLeast(Lifecycle.State.STARTED)
                conversations.forEach { conversation ->
                    val message = conversation.lastMessage ?: return@forEach
                    if (message.outgoing || message.status != MessageStatus.UNREAD || !announced.add(message.id)) return@forEach
                    if (background) PushPresentation.showOrDefer(context, conversation.name, message.body, conversation.peer.toString(), conversation.unreadCount)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            lifecycle.currentStateFlow.collect { state ->
                if (state.isAtLeast(Lifecycle.State.STARTED)) {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.activeNotifications.filter { it.notification.channelId == CHANNEL && it.id != PushWakeService.activeNotificationId }
                        .forEach { manager.cancel(it.tag, it.id) }
                }
            }
        }
    }

    private companion object {
        const val CHANNEL = "messages"
    }
}

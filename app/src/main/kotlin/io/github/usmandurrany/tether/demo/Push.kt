package io.github.usmandurrany.tether.demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.im.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Push notifications through Firebase Cloud Messaging and a push app server
 * such as [tether-push](https://github.com/usmandurrany/tether-push).
 *
 * The whole flow, with the SDK calls in [TetherSession]:
 * 1. Get the FCM token and register it with the app server
 *    (`chat.registerPush(PushAppServer.p2(server, token, installId))`). The SDK
 *    then tells the account's server (`<enable/>`) on every login.
 * 2. When the app is in the background the SDK closes the connection, so the
 *    server sends new messages to the app server, which sends an FCM message.
 * 3. [DemoPushService] gets it and calls `chat.wakeForPush()`: the SDK connects,
 *    the waiting messages arrive, and [MessageNotifier] shows them.
 *
 * Firebase is only set up when the build has an `app/google-services.json`
 * (see the README); without it [available] is false and the demo works as before.
 */
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

    /** The app server's address, such as `myapp-push@sure.im/push` for tether-push. */
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
        (application as DemoApp).pushReceived()
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
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH))
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
                    if (background) notify(conversation.name, message.body, conversation.peer.toString(), conversation.unreadCount)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            lifecycle.currentStateFlow.collect { state ->
                if (state.isAtLeast(Lifecycle.State.STARTED)) NotificationManagerCompat.from(context).cancelAll()
            }
        }
    }

    @SuppressLint("MissingPermission") // checked by canNotify, and a late revoke is caught
    private fun notify(title: String, text: String, peer: String, unread: Int) {
        if (!DemoPush.canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text.ifBlank { "New message" })
            .setNumber(unread)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(peer.hashCode(), notification)
        } catch (e: SecurityException) {
            // The permission was revoked meanwhile.
        }
    }

    private companion object {
        const val CHANNEL = "messages"
    }
}

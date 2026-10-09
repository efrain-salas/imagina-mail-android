package net.thunderbird.android.imagina

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Firebase Cloud Messaging for Imagina Mail: Imagina watches each mailbox and sends a high-priority data message
 * when mail arrives, which lets the app start work from the background (IMAGINA.md, «Notificaciones»). It only
 * hands over to [ImaginaPushHandler]; the app never gets a message's content because it has none.
 */
class ImaginaMessagingService : FirebaseMessagingService(), KoinComponent {
    private val handler: ImaginaPushHandler by inject()

    // Deprecated since firebase-messaging 25.1 in favour of onRegistered(), which hands over an installation id
    // instead of the FCM token Imagina's contract asks for (IMAGINA.md, «Pendiente»)
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        handler.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        handler.onMessage(message.data)
    }
}

package net.thunderbird.android.imagina

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.thunderbird.core.logging.Logger

/**
 * Where the Firebase token of this phone comes from. Firebase is only there when the build has
 * `app-imagina/google-services.json` (IMAGINA.md, «Notificaciones»); without it there is no push and the
 * accounts check mail every 15 minutes.
 */
interface ImaginaPushTokenSource {
    /** Whether this build has Firebase configured. */
    val isAvailable: Boolean

    /** The token of this phone, or `null` when Firebase is not there or could not give one just now. */
    suspend fun currentToken(): String?
}

class FirebaseImaginaPushTokenSource(
    private val context: Context,
    private val logger: Logger,
) : ImaginaPushTokenSource {

    /** Firebase starts itself from the string resources made out of `google-services.json`; with none it does not. */
    override val isAvailable: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    // getToken() is deprecated since firebase-messaging 25.1 (see ImaginaMessagingService.onNewToken)
    @Suppress("DEPRECATION")
    override suspend fun currentToken(): String? {
        if (!isAvailable) return null

        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result else null
                if (token == null) {
                    logger.warn(TAG, task.exception) { "Firebase gave no token" }
                }
                continuation.resume(token?.takeIf { it.isNotEmpty() })
            }
        }
    }

    private companion object {
        const val TAG = "FirebaseImaginaPushTokenSource"
    }
}

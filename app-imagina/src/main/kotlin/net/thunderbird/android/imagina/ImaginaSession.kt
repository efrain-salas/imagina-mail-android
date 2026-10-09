package net.thunderbird.android.imagina

import android.content.Context
import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.json.JSONException

/**
 * The person's sign-in with Imagina: keeps the OAuth state and gives a valid access token whenever
 * Imagina's API has to be called.
 */
interface ImaginaSession {
    /** The person just signed in: keep the state OAuth returned. */
    fun signedIn(authorizationState: AuthorizationState)

    /**
     * An access token that is valid now.
     *
     * @throws ImaginaSignInRequiredException when the refresh token is gone or Imagina refuses it.
     * @throws ImaginaNetworkException when Imagina could not be reached.
     */
    suspend fun accessToken(): String
}

/**
 * Access tokens last 15 minutes and the refresh token 7 days, and it rotates: every refresh gives
 * a new one that must be stored before it is used again. That is why renewing is serialised here.
 */
class AppAuthImaginaSession(
    private val context: Context,
    private val store: ImaginaDeviceStore,
) : ImaginaSession {
    private val mutex = Mutex()

    override fun signedIn(authorizationState: AuthorizationState) {
        store.authState = authorizationState.value
        store.needsSignIn = false
    }

    override suspend fun accessToken(): String = mutex.withLock {
        val authState = loadAuthState()
        val currentAccessToken = authState.accessToken
        if (currentAccessToken != null && !authState.needsTokenRefresh) {
            currentAccessToken
        } else {
            if (authState.refreshToken == null) throw ImaginaSignInRequiredException()

            refresh(authState).also { store.authState = authState.jsonSerializeString() }
        }
    }

    private fun loadAuthState(): AuthState {
        val json = store.authState ?: throw ImaginaSignInRequiredException()

        return try {
            AuthState.jsonDeserialize(json)
        } catch (e: JSONException) {
            throw ImaginaSignInRequiredException(e)
        }
    }

    /** AppAuth renews through an `AsyncTask`, so it is started and answered on the main thread. */
    private suspend fun refresh(authState: AuthState): String = withContext(Dispatchers.Main) {
        val service = AuthorizationService(context)
        try {
            suspendCancellableCoroutine { continuation ->
                authState.performActionWithFreshTokens(service) { accessToken, _, exception ->
                    if (accessToken != null) {
                        continuation.resume(accessToken)
                    } else {
                        continuation.resumeWithException(exception.toImaginaException())
                    }
                }
            }
        } finally {
            service.dispose()
        }
    }

    /** A token error (`invalid_grant`…) means the sign-in is over; anything else may pass. */
    private fun AuthorizationException?.toImaginaException(): ImaginaException = when {
        this == null -> ImaginaNetworkException(IllegalStateException("Imagina gave no access token"))
        type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR -> ImaginaSignInRequiredException(this)
        else -> ImaginaNetworkException(this)
    }
}

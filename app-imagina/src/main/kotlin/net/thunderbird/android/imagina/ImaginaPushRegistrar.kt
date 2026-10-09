package net.thunderbird.android.imagina

import net.thunderbird.core.logging.Logger

/**
 * Keeps Imagina's copy of this phone's Firebase token up to date (`PUT` and `DELETE /devices/{id}/push`).
 *
 * Push is [isActive] only when this build has Firebase and Imagina accepted the token for the device in use:
 * only then are the accounts' own mail checks relaxed to once an hour.
 */
class ImaginaPushRegistrar(
    private val api: ImaginaDevicesApi,
    private val store: ImaginaDeviceStore,
    private val tokenSource: ImaginaPushTokenSource,
    private val logger: Logger,
) {
    /** Imagina has the token of this device and will wake the app. */
    val isActive: Boolean
        get() {
            val deviceId = store.deviceId
            return tokenSource.isAvailable && deviceId != null && store.pushRegisteredDeviceId == deviceId
        }

    /** The phone is connected, Firebase is there and Imagina does not have the token yet. */
    val needsRegistration: Boolean
        get() = tokenSource.isAvailable && store.isConnected && !isActive

    /** Firebase gave this phone a new token: Imagina's copy is out of date until [register] runs again. */
    fun onNewToken(token: String) {
        if (!store.isConnected || token == store.pushToken) return

        store.pushToken = token
        store.pushRegisteredDeviceId = null
    }

    /**
     * Gives Imagina the token when it does not have it for [deviceId].
     *
     * @return whether push is active now. Without Firebase, without a token (Firebase calls `onNewToken` when it
     *   has one) or when Imagina refuses the token it is not, and nothing has to be retried.
     * @throws ImaginaDeviceRevokedException when Imagina disconnected the device.
     * @throws ImaginaException when Imagina could not be asked just now; it should be tried again later.
     */
    suspend fun register(accessToken: String, deviceId: String): Boolean {
        val token = if (tokenSource.isAvailable) tokenSource.currentToken() else null
        if (token != null) {
            if (token != store.pushToken) {
                store.pushToken = token
                store.pushRegisteredDeviceId = null
            }
            if (store.pushRegisteredDeviceId != deviceId) {
                upload(accessToken, deviceId, token)
            }
        }

        return isActive
    }

    private suspend fun upload(accessToken: String, deviceId: String, token: String) {
        try {
            api.registerPushToken(accessToken, deviceId, token)
        } catch (error: ImaginaApiException) {
            if (!error.isRefusalOfTheToken()) throw error

            logger.warn(TAG, error) { "Imagina refused the Firebase token" }
            return
        }

        // Firebase may have rotated the token while Imagina was answering: that one still has to go
        if (store.pushToken == token) {
            store.pushRegisteredDeviceId = deviceId
        }
    }

    /** Asks Imagina to forget the token and forgets it here, whatever the answer. */
    suspend fun unregister(accessToken: String, deviceId: String) {
        try {
            api.forgetPushToken(accessToken, deviceId)
        } finally {
            store.pushToken = null
            store.pushRegisteredDeviceId = null
        }
    }

    /** A 4xx about the token itself, not about the sign-in, the device or the rate of calls. */
    private fun ImaginaApiException.isRefusalOfTheToken(): Boolean =
        status in CLIENT_ERRORS && status !in NOT_ABOUT_THE_TOKEN

    private companion object {
        const val TAG = "ImaginaPushRegistrar"
        val CLIENT_ERRORS = 400..499
        val NOT_ABOUT_THE_TOKEN = setOf(401, 403, 404, 408, 429)
    }
}

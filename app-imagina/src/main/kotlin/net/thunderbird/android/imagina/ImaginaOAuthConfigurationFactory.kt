package net.thunderbird.android.imagina

import net.thunderbird.android.BuildConfig
import net.thunderbird.core.common.oauth.OAuthConfiguration
import net.thunderbird.core.common.oauth.OAuthConfigurationFactory

/**
 * The only OAuth provider of Imagina Mail: Imagina's identity server, where a person signs in with
 * their mobile number and a one-time code. The token it gives (scope `cloud.devices`) is used to
 * ask Imagina's devices API for the person's mailboxes, not to log in to the mail servers (Migadu
 * takes a password per device and mailbox, which the API hands out).
 */
class ImaginaOAuthConfigurationFactory : OAuthConfigurationFactory {
    override fun createConfigurations(): Map<List<String>, OAuthConfiguration> {
        return mapOf(
            listOf(ImaginaAuth.HOST) to OAuthConfiguration(
                clientId = BuildConfig.IMAGINA_OAUTH_CLIENT_ID,
                scopes = ImaginaAuth.SCOPES,
                authorizationEndpoint = "https://${ImaginaAuth.HOST}/oauth/authorize",
                tokenEndpoint = "https://${ImaginaAuth.HOST}/oauth/token",
                redirectUri = "${BuildConfig.APPLICATION_ID}://oauth2redirect",
            ),
        )
    }
}

object ImaginaAuth {
    val HOST: String = BuildConfig.IMAGINA_AUTH_HOST

    /** `cloud.devices` lets the app call Imagina's devices API. */
    val SCOPES: List<String> = listOf("openid", "cloud.devices")

    /** Base URL of Imagina's devices API. */
    val DEVICES_API_URL: String = "https://$HOST/api/v1/cloud/devices"
}

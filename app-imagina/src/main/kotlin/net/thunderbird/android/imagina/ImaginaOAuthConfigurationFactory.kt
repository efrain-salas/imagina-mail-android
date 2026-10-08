package net.thunderbird.android.imagina

import net.thunderbird.android.BuildConfig
import net.thunderbird.core.common.oauth.OAuthConfiguration
import net.thunderbird.core.common.oauth.OAuthConfigurationFactory

/**
 * The only OAuth provider of Imagina Mail: Imagina's identity server, where a person signs in with
 * their mobile number and a one-time code. It is used to get the person's mailboxes from Imagina,
 * not to log in to the mail servers (Migadu takes a password per device).
 */
class ImaginaOAuthConfigurationFactory : OAuthConfigurationFactory {
    override fun createConfigurations(): Map<List<String>, OAuthConfiguration> {
        return mapOf(
            listOf(ImaginaAuth.HOST) to OAuthConfiguration(
                clientId = BuildConfig.IMAGINA_OAUTH_CLIENT_ID,
                scopes = listOf("openid", "profile", "email"),
                authorizationEndpoint = "https://${ImaginaAuth.HOST}/oauth/authorize",
                tokenEndpoint = "https://${ImaginaAuth.HOST}/oauth/token",
                redirectUri = "${BuildConfig.APPLICATION_ID}://oauth2redirect",
            ),
        )
    }
}

object ImaginaAuth {
    const val HOST = "auth.imagina.build"
}

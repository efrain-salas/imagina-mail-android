package net.thunderbird.android.imagina

import app.k9mail.feature.account.common.domain.entity.Account
import app.k9mail.feature.account.common.domain.entity.AccountOptions
import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import app.k9mail.feature.account.setup.AccountSetupExternalContract.AccountCreator
import app.k9mail.feature.account.setup.AccountSetupExternalContract.AccountCreator.AccountCreatorResult
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.store.imap.ImapStoreSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.thunderbird.android.BuildConfig
import org.json.JSONObject

/**
 * Turns an Imagina sign-in into mail accounts: asks Imagina who signed in and which mailboxes they
 * use, and creates one account per mailbox with the credential Imagina made for this device.
 *
 * Prototype (docs/PLAN_CLOUD.md, C0.9): Imagina's devices API (C7.1) does not exist yet, so the
 * person comes from /oauth/userinfo and the mailbox from the build (imagina.properties).
 */
class ImaginaAccountProvisioner(
    private val accountCreator: AccountCreator,
) {
    suspend fun provision(authorizationState: AuthorizationState): Result<String> = runCatching {
        val json = requireNotNull(authorizationState.value) { "Imagina no ha devuelto la sesión" }
        val accessToken = requireNotNull(AuthState.jsonDeserialize(json).accessToken) { "Imagina no ha devuelto el token" }
        val person = userInfo(accessToken)
        val mailboxes = mailboxesFor(person)

        mailboxes.map { mailbox -> createAccount(mailbox, person) }.first()
    }

    private suspend fun userInfo(accessToken: String): Person = withContext(Dispatchers.IO) {
        val connection = URL("https://${ImaginaAuth.HOST}/oauth/userinfo").openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Imagina respondió ${connection.responseCode}" }
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            Person(name = body.optString("name"), email = body.optString("email"))
        } finally {
            connection.disconnect()
        }
    }

    private fun mailboxesFor(person: Person): List<Mailbox> = listOf(
        Mailbox(
            address = BuildConfig.IMAGINA_TEST_MAIL_ADDRESS,
            username = BuildConfig.IMAGINA_TEST_MAIL_USERNAME,
            password = BuildConfig.IMAGINA_TEST_MAIL_PASSWORD,
        ),
    )

    private suspend fun createAccount(mailbox: Mailbox, person: Person): String {
        val account = Account(
            uuid = UUID.randomUUID().toString(),
            emailAddress = mailbox.address,
            incomingServerSettings = ServerSettings(
                type = "imap",
                host = "imap.migadu.com",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = mailbox.username,
                password = mailbox.password,
                clientCertificateAlias = null,
                extra = ImapStoreSettings.createExtra(
                    autoDetectNamespace = true,
                    pathPrefix = null,
                    useCompression = true,
                    sendClientInfo = true,
                ),
            ),
            outgoingServerSettings = ServerSettings(
                type = "smtp",
                host = "smtp.migadu.com",
                port = 465,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = mailbox.username,
                password = mailbox.password,
                clientCertificateAlias = null,
            ),
            authorizationState = null,
            specialFolderSettings = null,
            options = AccountOptions(
                accountName = mailbox.address,
                displayName = person.name.ifBlank { mailbox.address },
                emailSignature = null,
                checkFrequencyInMinutes = 15,
                messageDisplayCount = 25,
                showNotification = true,
            ),
        )

        return when (val result = accountCreator.createAccount(account)) {
            is AccountCreatorResult.Success -> result.accountUuid
            is AccountCreatorResult.Error -> error(result.message)
        }
    }

    data class Person(val name: String, val email: String)

    private data class Mailbox(val address: String, val username: String, val password: String)
}

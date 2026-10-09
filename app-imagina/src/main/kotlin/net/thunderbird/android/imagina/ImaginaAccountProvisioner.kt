package net.thunderbird.android.imagina

import app.k9mail.feature.account.common.domain.entity.Account
import app.k9mail.feature.account.common.domain.entity.AccountOptions
import app.k9mail.feature.account.edit.AccountEditExternalContract.AccountServerSettingsUpdater
import app.k9mail.feature.account.edit.AccountEditExternalContract.AccountUpdaterResult
import app.k9mail.feature.account.setup.AccountSetupExternalContract.AccountCreator
import app.k9mail.feature.account.setup.AccountSetupExternalContract.AccountCreator.AccountCreatorResult
import com.fsck.k9.Preferences
import com.fsck.k9.job.K9JobManager
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.store.imap.ImapStoreSettings
import java.util.UUID
import net.thunderbird.feature.account.settings.api.BackgroundAccountRemover

/**
 * The mail accounts of this phone, as far as Imagina's devices are concerned: one account per
 * mailbox, made with the credential Imagina created for this device.
 */
interface ImaginaLocalAccounts {
    /** Creates the account of [mailbox], looking for mail by itself every [checkFrequencyMinutes]; returns its uuid. */
    suspend fun create(mailbox: ImaginaMailAccount, accountName: String, checkFrequencyMinutes: Int): String

    /** Makes the account look for mail by itself every [minutes] and reschedules its checks if that changed. */
    fun setCheckFrequency(accountUuid: String, minutes: Int)

    /** Puts the servers and credential Imagina gave in the account that already exists. */
    suspend fun updateCredentials(accountUuid: String, mailbox: ImaginaMailAccount)

    /** Removes the account and its messages in the background. */
    fun remove(accountUuid: String)

    fun exists(accountUuid: String): Boolean
}

/** How often (in minutes) the accounts look for mail by themselves. */
object ImaginaCheckFrequency {
    /** The only way to get mail: Imagina Mail has no push of its own. */
    const val WITHOUT_PUSH = 15

    /** A safety net: Imagina wakes the app when mail arrives, and this catches a push that never came. */
    const val WITH_PUSH = 60
}

/**
 * Thunderbird's own pieces do the work: the [AccountCreator] that account setup uses, the
 * [AccountServerSettingsUpdater] of the server settings screens and the [BackgroundAccountRemover]
 * of «Eliminar cuenta».
 */
class ImaginaAccountProvisioner(
    private val accountCreator: AccountCreator,
    private val serverSettingsUpdater: AccountServerSettingsUpdater,
    private val accountRemover: BackgroundAccountRemover,
    private val preferences: Preferences,
    private val jobManager: K9JobManager,
) : ImaginaLocalAccounts {

    override suspend fun create(mailbox: ImaginaMailAccount, accountName: String, checkFrequencyMinutes: Int): String {
        val accountUuid = UUID.randomUUID().toString()

        val account = try {
            Account(
                uuid = accountUuid,
                emailAddress = mailbox.address,
                incomingServerSettings = mailbox.incomingServerSettings(),
                outgoingServerSettings = mailbox.outgoingServerSettings(),
                authorizationState = null,
                specialFolderSettings = null,
                options = AccountOptions(
                    accountName = accountName,
                    displayName = mailbox.name.ifBlank { mailbox.address },
                    emailSignature = null,
                    checkFrequencyInMinutes = checkFrequencyMinutes,
                    messageDisplayCount = MESSAGE_DISPLAY_COUNT,
                    showNotification = true,
                ),
            )
        } catch (error: IllegalArgumentException) {
            throw ImaginaAccountException("Imagina sent a mailbox that cannot be used", error)
        }

        return when (val result = accountCreator.createAccount(account)) {
            is AccountCreatorResult.Success -> result.accountUuid
            is AccountCreatorResult.Error -> throw ImaginaAccountException(result.message)
        }
    }

    override suspend fun updateCredentials(accountUuid: String, mailbox: ImaginaMailAccount) {
        try {
            updateServerSettings(accountUuid, isIncoming = true, mailbox.incomingServerSettings())
            updateServerSettings(accountUuid, isIncoming = false, mailbox.outgoingServerSettings())
        } catch (error: IllegalArgumentException) {
            throw ImaginaAccountException("Imagina sent a mailbox that cannot be used", error)
        }
    }

    override fun setCheckFrequency(accountUuid: String, minutes: Int) {
        val account = preferences.getAccount(accountUuid) ?: return

        if (account.updateAutomaticCheckIntervalMinutes(minutes)) {
            preferences.saveAccount(account)
            jobManager.scheduleMailSync(account)
        }
    }

    override fun remove(accountUuid: String) {
        accountRemover.removeAccountAsync(accountUuid)
    }

    override fun exists(accountUuid: String): Boolean = preferences.getAccount(accountUuid) != null

    private suspend fun updateServerSettings(accountUuid: String, isIncoming: Boolean, settings: ServerSettings) {
        val result = serverSettingsUpdater.updateServerSettings(
            accountUuid = accountUuid,
            isIncoming = isIncoming,
            serverSettings = settings,
            authorizationState = null,
        )
        if (result !is AccountUpdaterResult.Success) {
            throw ImaginaAccountException("Could not update the account $accountUuid: $result")
        }
    }

    private companion object {
        const val MESSAGE_DISPLAY_COUNT = 25
    }
}

internal fun ImaginaMailAccount.incomingServerSettings() = ServerSettings(
    type = "imap",
    host = imap.host,
    port = imap.port,
    connectionSecurity = imap.connectionSecurity(),
    authenticationType = AuthType.PLAIN,
    username = username,
    password = password,
    clientCertificateAlias = null,
    extra = ImapStoreSettings.createExtra(
        autoDetectNamespace = true,
        pathPrefix = null,
        useCompression = true,
        sendClientInfo = true,
    ),
)

internal fun ImaginaMailAccount.outgoingServerSettings() = ServerSettings(
    type = "smtp",
    host = smtp.host,
    port = smtp.port,
    connectionSecurity = smtp.connectionSecurity(),
    authenticationType = AuthType.PLAIN,
    username = username,
    password = password,
    clientCertificateAlias = null,
)

internal fun ImaginaServer.connectionSecurity(): ConnectionSecurity = when (security.lowercase()) {
    "ssl", "tls" -> ConnectionSecurity.SSL_TLS_REQUIRED
    "starttls" -> ConnectionSecurity.STARTTLS_REQUIRED
    else -> throw ImaginaAccountException("Unsupported connection security: $security")
}

package net.thunderbird.android.imagina

import app.k9mail.feature.account.common.domain.entity.AuthorizationState

internal fun mailbox(
    address: String,
    company: String = "Casa Pepe",
    password: String = "secret-$address",
) = ImaginaMailAccount(
    address = address,
    name = "Ana",
    company = company,
    username = "login+$address",
    password = password,
    imap = ImaginaServer(host = "imap.migadu.com", port = 993, security = "ssl"),
    smtp = ImaginaServer(host = "smtp.migadu.com", port = 465, security = "ssl"),
)

internal fun status(
    revoked: Boolean = false,
    changed: Boolean = false,
) = ImaginaDeviceStatus(revoked = revoked, changed = changed, mailboxes = emptyList())

internal class InMemoryImaginaDeviceStore : ImaginaDeviceStore {
    override var deviceId: String? = null
    override var authState: String? = null
    override var needsSignIn: Boolean = false
    override var lastSyncAt: Long = 0L
    override var accounts: List<ImaginaManagedAccount> = emptyList()
    override var pushToken: String? = null
    override var pushRegisteredDeviceId: String? = null
    override var checkFrequencyMinutes: Int = 0

    override fun clear() {
        deviceId = null
        authState = null
        needsSignIn = false
        lastSyncAt = 0L
        accounts = emptyList()
        pushToken = null
        pushRegisteredDeviceId = null
        checkFrequencyMinutes = 0
    }
}

internal class FakeImaginaSession(private val store: ImaginaDeviceStore) : ImaginaSession {
    var failure: ImaginaException? = null
    var signedInWith: AuthorizationState? = null

    override fun signedIn(authorizationState: AuthorizationState) {
        signedInWith = authorizationState
        store.authState = authorizationState.value
        store.needsSignIn = false
    }

    override suspend fun accessToken(): String {
        failure?.let { throw it }
        return ACCESS_TOKEN
    }

    companion object {
        const val ACCESS_TOKEN = "access-token"
    }
}

internal class FakeImaginaSyncScheduler : ImaginaSyncScheduler {
    var scheduled = 0
    var cancelled = 0
    var syncsSoon = 0
    val inboxChecks = mutableListOf<String>()

    override fun schedule() {
        scheduled++
    }

    override fun syncSoon() {
        syncsSoon++
    }

    override fun checkInbox(accountUuid: String) {
        inboxChecks += accountUuid
    }

    override fun cancel() {
        cancelled++
    }
}

internal class FakeImaginaLocalAccounts : ImaginaLocalAccounts {
    /** uuid -> the mailbox whose credential the account has. */
    val accounts = linkedMapOf<String, ImaginaMailAccount>()
    val createdNames = mutableListOf<String>()

    /** uuid -> how often the account looks for mail by itself, in minutes. */
    val checkFrequencies = linkedMapOf<String, Int>()
    val removed = mutableListOf<String>()
    var failOnCreate: Set<String> = emptySet()
    private var counter = 0

    override suspend fun create(mailbox: ImaginaMailAccount, accountName: String, checkFrequencyMinutes: Int): String {
        if (mailbox.address in failOnCreate) throw ImaginaAccountException("cannot create ${mailbox.address}")

        val uuid = "account-${++counter}"
        accounts[uuid] = mailbox
        createdNames += accountName
        checkFrequencies[uuid] = checkFrequencyMinutes
        return uuid
    }

    override fun setCheckFrequency(accountUuid: String, minutes: Int) {
        checkFrequencies[accountUuid] = minutes
    }

    override suspend fun updateCredentials(accountUuid: String, mailbox: ImaginaMailAccount) {
        accounts[accountUuid] = mailbox
    }

    override fun remove(accountUuid: String) {
        accounts.remove(accountUuid)
        checkFrequencies.remove(accountUuid)
        removed += accountUuid
    }

    override fun exists(accountUuid: String): Boolean = accountUuid in accounts

    fun uuidOf(address: String): String = accounts.entries.first { it.value.address == address }.key
}

internal class FakeImaginaDevicesApi : ImaginaDevicesApi {
    var registration: ImaginaDeviceRegistration? = null
    var statusResult: () -> ImaginaDeviceStatus = { status() }
    var refreshResult: () -> ImaginaDeviceRefresh = { ImaginaDeviceRefresh(emptyList(), emptyList()) }
    var registerFailure: ImaginaException? = null

    /** What `PUT /push` and `DELETE /push` do: `null` answers 204. */
    var pushFailure: ImaginaException? = null

    val registeredNames = mutableListOf<String>()
    val disconnected = mutableListOf<String>()

    /** device id -> token, for each `PUT /push` Imagina answered 204 to. */
    val pushTokens = linkedMapOf<String, String>()
    val pushRegistrations = mutableListOf<Pair<String, String>>()
    val pushForgotten = mutableListOf<String>()
    var statusRequests = 0
    var refreshRequests = 0
    var lastAccessToken: String? = null

    override suspend fun registerDevice(accessToken: String, name: String): ImaginaDeviceRegistration {
        lastAccessToken = accessToken
        registeredNames += name
        registerFailure?.let { throw it }
        return checkNotNull(registration)
    }

    override suspend fun getDevice(accessToken: String, deviceId: String): ImaginaDeviceStatus {
        lastAccessToken = accessToken
        statusRequests++
        return statusResult()
    }

    override suspend fun refreshDevice(accessToken: String, deviceId: String): ImaginaDeviceRefresh {
        lastAccessToken = accessToken
        refreshRequests++
        return refreshResult()
    }

    override suspend fun disconnectDevice(accessToken: String, deviceId: String) {
        disconnected += deviceId
    }

    override suspend fun registerPushToken(accessToken: String, deviceId: String, token: String) {
        lastAccessToken = accessToken
        pushRegistrations += deviceId to token
        pushFailure?.let { throw it }
        pushTokens[deviceId] = token
    }

    override suspend fun forgetPushToken(accessToken: String, deviceId: String) {
        lastAccessToken = accessToken
        pushForgotten += deviceId
        pushFailure?.let { throw it }
        pushTokens.remove(deviceId)
    }
}

/** Firebase, as far as the app can tell: there or not, with a token or not. */
internal class FakeImaginaPushTokenSource(
    override var isAvailable: Boolean = true,
    var token: String? = "fcm-token-1",
) : ImaginaPushTokenSource {
    override suspend fun currentToken(): String? = token.takeIf { isAvailable }
}

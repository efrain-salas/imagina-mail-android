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

    override fun clear() {
        deviceId = null
        authState = null
        needsSignIn = false
        lastSyncAt = 0L
        accounts = emptyList()
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

    override fun schedule() {
        scheduled++
    }

    override fun syncSoon() = Unit

    override fun cancel() {
        cancelled++
    }
}

internal class FakeImaginaLocalAccounts : ImaginaLocalAccounts {
    /** uuid -> the mailbox whose credential the account has. */
    val accounts = linkedMapOf<String, ImaginaMailAccount>()
    val createdNames = mutableListOf<String>()
    val removed = mutableListOf<String>()
    var failOnCreate: Set<String> = emptySet()
    private var counter = 0

    override suspend fun create(mailbox: ImaginaMailAccount, accountName: String): String {
        if (mailbox.address in failOnCreate) throw ImaginaAccountException("cannot create ${mailbox.address}")

        val uuid = "account-${++counter}"
        accounts[uuid] = mailbox
        createdNames += accountName
        return uuid
    }

    override suspend fun updateCredentials(accountUuid: String, mailbox: ImaginaMailAccount) {
        accounts[accountUuid] = mailbox
    }

    override fun remove(accountUuid: String) {
        accounts.remove(accountUuid)
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

    val registeredNames = mutableListOf<String>()
    val disconnected = mutableListOf<String>()
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
}

package net.thunderbird.android.imagina

import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.testing.TestLogger

@OptIn(ExperimentalCoroutinesApi::class)
class ImaginaDeviceSynchronizerTest {
    private val api = FakeImaginaDevicesApi()
    private val store = InMemoryImaginaDeviceStore()
    private val session = FakeImaginaSession(store)
    private val localAccounts = FakeImaginaLocalAccounts()
    private val scheduler = FakeImaginaSyncScheduler()

    private val authorizationState = AuthorizationState("oauth-state")

    private fun synchronizer() = ImaginaDeviceSynchronizer(
        api = api,
        session = session,
        store = store,
        localAccounts = localAccounts,
        scheduler = scheduler,
        logger = TestLogger(),
        dispatcher = UnconfinedTestDispatcher(),
        clock = { NOW },
    )

    private fun registers(vararg mailboxes: ImaginaMailAccount) {
        api.registration = ImaginaDeviceRegistration(deviceId = DEVICE_ID, mailboxes = mailboxes.toList())
    }

    /** A phone that is connected with the given mailboxes, as the synchronizer leaves it. */
    private suspend fun connected(vararg mailboxes: ImaginaMailAccount) {
        registers(*mailboxes)
        synchronizer().connect(authorizationState, DEVICE_NAME)
        api.registeredNames.clear()
        scheduler.scheduled = 0
    }

    @Test
    fun `connect registers the device and creates one account per mailbox`() = runTest {
        registers(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))

        val firstAccount = synchronizer().connect(authorizationState, DEVICE_NAME)

        assertThat(api.registeredNames).containsExactly(DEVICE_NAME)
        assertThat(api.lastAccessToken).isEqualTo(FakeImaginaSession.ACCESS_TOKEN)
        assertThat(session.signedInWith).isEqualTo(authorizationState)
        assertThat(localAccounts.accounts.values.map { it.address })
            .containsExactly("ana@casapepe.com", "info@casapepe.com")
        assertThat(firstAccount).isEqualTo(localAccounts.uuidOf("ana@casapepe.com"))
        assertThat(store.deviceId).isEqualTo(DEVICE_ID)
        assertThat(store.accounts.map { it.address }).containsExactly("ana@casapepe.com", "info@casapepe.com")
        assertThat(store.lastSyncAt).isEqualTo(NOW)
        assertThat(scheduler.scheduled).isEqualTo(1)
    }

    @Test
    fun `connect names the accounts with the company only when there are several companies`() = runTest {
        registers(mailbox("ana@casapepe.com", company = "Casa Pepe"), mailbox("ana@cafe.es", company = "Café Sol"))
        synchronizer().connect(authorizationState, DEVICE_NAME)

        assertThat(localAccounts.createdNames)
            .containsExactly("ana@casapepe.com · Casa Pepe", "ana@cafe.es · Café Sol")
    }

    @Test
    fun `connect names the accounts with the address when there is one company`() = runTest {
        registers(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))
        synchronizer().connect(authorizationState, DEVICE_NAME)

        assertThat(localAccounts.createdNames).containsExactly("ana@casapepe.com", "info@casapepe.com")
    }

    @Test
    fun `connect with a device that is still valid does not register another`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        val firstAccount = localAccounts.uuidOf("ana@casapepe.com")

        val result = synchronizer().connect(authorizationState, DEVICE_NAME)

        assertThat(api.registeredNames).isEmpty()
        assertThat(api.statusRequests).isEqualTo(1)
        assertThat(api.refreshRequests).isEqualTo(0)
        assertThat(result).isEqualTo(firstAccount)
        assertThat(localAccounts.accounts.size).isEqualTo(1)
    }

    @Test
    fun `connect with a device that changed brings the new mailboxes`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        api.statusResult = { status(changed = true) }
        api.refreshResult =
            { ImaginaDeviceRefresh(added = listOf(mailbox("info@casapepe.com")), removed = emptyList()) }

        synchronizer().connect(authorizationState, DEVICE_NAME)

        assertThat(localAccounts.accounts.values.map { it.address })
            .containsExactly("ana@casapepe.com", "info@casapepe.com")
    }

    @Test
    fun `connect after the device was revoked registers a new one and renews the credentials in place`() = runTest {
        connected(mailbox("ana@casapepe.com", password = "old"), mailbox("old@casapepe.com"))
        val anaAccount = localAccounts.uuidOf("ana@casapepe.com")
        val oldAccount = localAccounts.uuidOf("old@casapepe.com")
        api.statusResult = { status(revoked = true) }
        api.registration = ImaginaDeviceRegistration(
            deviceId = "device-2",
            mailboxes = listOf(mailbox("ana@casapepe.com", password = "new")),
        )

        synchronizer().connect(authorizationState, DEVICE_NAME)

        assertThat(store.deviceId).isEqualTo("device-2")
        assertThat(localAccounts.accounts.keys).containsOnly(anaAccount)
        assertThat(localAccounts.accounts.getValue(anaAccount).password).isEqualTo("new")
        assertThat(localAccounts.removed).containsExactly(oldAccount)
        assertThat(store.accounts.map { it.address }).containsExactly("ana@casapepe.com")
    }

    @Test
    fun `connect registers a new device when Imagina does not know the stored one`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        api.statusResult = { throw ImaginaApiException(status = 404, code = "not_found", serverMessage = null) }
        api.registration =
            ImaginaDeviceRegistration(deviceId = "device-2", mailboxes = listOf(mailbox("ana@casapepe.com")))

        synchronizer().connect(authorizationState, DEVICE_NAME)

        assertThat(store.deviceId).isEqualTo("device-2")
        assertThat(localAccounts.accounts.size).isEqualTo(1)
    }

    @Test
    fun `connect leaves nothing behind when an account cannot be created`() = runTest {
        registers(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))
        localAccounts.failOnCreate = setOf("info@casapepe.com")

        assertFailure { synchronizer().connect(authorizationState, DEVICE_NAME) }
            .isInstanceOf<ImaginaAccountException>()

        assertThat(api.disconnected).containsExactly(DEVICE_ID)
        assertThat(localAccounts.accounts.size).isEqualTo(0)
        assertThat(store.deviceId).isNull()
        assertThat(store.authState).isNull()
        assertThat(store.accounts).isEmpty()
        assertThat(scheduler.scheduled).isEqualTo(0)
    }

    @Test
    fun `connect with no mailboxes disconnects the device and fails`() = runTest {
        registers()

        assertFailure { synchronizer().connect(authorizationState, DEVICE_NAME) }
            .isInstanceOf<ImaginaNoMailboxesException>()

        assertThat(api.disconnected).containsExactly(DEVICE_ID)
        assertThat(store.deviceId).isNull()
    }

    @Test
    fun `connect passes on the refusal of Imagina`() = runTest {
        api.registerFailure = ImaginaApiException(status = 403, code = "no_seat", serverMessage = "No tienes buzón")

        assertFailure { synchronizer().connect(authorizationState, DEVICE_NAME) }
            .isInstanceOf<ImaginaApiException>()

        assertThat(store.deviceId).isNull()
        assertThat(store.isConnected).isFalse()
    }

    @Test
    fun `sync does nothing when the phone is not connected`() = runTest {
        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.NotConnected)
        assertThat(api.statusRequests).isEqualTo(0)
    }

    @Test
    fun `sync with nothing changed only asks the device`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        store.needsSignIn = true

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.Synced)

        assertThat(api.refreshRequests).isEqualTo(0)
        assertThat(store.needsSignIn).isFalse()
    }

    @Test
    fun `sync adds the accounts of new mailboxes and removes those of removed ones`() = runTest {
        connected(mailbox("ana@casapepe.com"), mailbox("old@casapepe.com"))
        api.statusResult = { status(changed = true) }
        api.refreshResult = {
            ImaginaDeviceRefresh(added = listOf(mailbox("info@casapepe.com")), removed = listOf("OLD@casapepe.com"))
        }

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.Synced)

        assertThat(localAccounts.accounts.values.map { it.address })
            .containsExactly("ana@casapepe.com", "info@casapepe.com")
        assertThat(store.accounts.map { it.address }).containsExactly("ana@casapepe.com", "info@casapepe.com")
    }

    @Test
    fun `sync renews the credential of a mailbox that already has an account`() = runTest {
        connected(mailbox("ana@casapepe.com", password = "old"))
        val account = localAccounts.uuidOf("ana@casapepe.com")
        api.statusResult = { status(changed = true) }
        api.refreshResult = {
            ImaginaDeviceRefresh(added = listOf(mailbox("ana@casapepe.com", password = "new")), removed = emptyList())
        }

        synchronizer().sync()

        assertThat(localAccounts.accounts.size).isEqualTo(1)
        assertThat(localAccounts.accounts.getValue(account).password).isEqualTo("new")
    }

    @Test
    fun `sync removes the accounts and the link when the device was revoked`() = runTest {
        connected(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))
        api.statusResult = { status(revoked = true) }

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.Revoked)

        assertThat(localAccounts.accounts.size).isEqualTo(0)
        assertThat(store.isConnected).isFalse()
        assertThat(store.authState).isNull()
        assertThat(store.accounts).isEmpty()
        assertThat(scheduler.cancelled).isEqualTo(1)
    }

    @Test
    fun `sync removes the accounts when the refresh says the device is gone`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        api.statusResult = { status(changed = true) }
        api.refreshResult = { throw ImaginaDeviceRevokedException() }

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.Revoked)

        assertThat(localAccounts.accounts.size).isEqualTo(0)
        assertThat(store.isConnected).isFalse()
    }

    @Test
    fun `sync keeps the accounts and asks to sign in when the token cannot be renewed`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        session.failure = ImaginaSignInRequiredException()

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.SignInRequired)

        assertThat(store.needsSignIn).isTrue()
        assertThat(store.isConnected).isTrue()
        assertThat(localAccounts.accounts.size).isEqualTo(1)
    }

    @Test
    fun `sync asks to sign in when Imagina refuses the token`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        api.statusResult = { throw ImaginaApiException(status = 401, code = "unauthenticated", serverMessage = null) }

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.SignInRequired)

        assertThat(store.needsSignIn).isTrue()
        assertThat(localAccounts.accounts.size).isEqualTo(1)
    }

    @Test
    fun `sync fails quietly when Imagina cannot be reached`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        api.statusResult = { throw ImaginaNetworkException(IOException("offline")) }

        assertThat(synchronizer().sync()).isInstanceOf<ImaginaSyncResult.Failed>()

        assertThat(store.needsSignIn).isFalse()
        assertThat(localAccounts.accounts.size).isEqualTo(1)
    }

    @Test
    fun `sync starts over with a new device when a delivered mailbox cannot be created`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        api.statusResult = { status(changed = true) }
        api.refreshResult =
            { ImaginaDeviceRefresh(added = listOf(mailbox("info@casapepe.com")), removed = emptyList()) }
        localAccounts.failOnCreate = setOf("info@casapepe.com")

        assertThat(synchronizer().sync()).isInstanceOf<ImaginaSyncResult.Failed>()

        assertThat(api.disconnected).containsExactly(DEVICE_ID)
        assertThat(store.deviceId).isNull()
        assertThat(store.needsSignIn).isTrue()
        assertThat(localAccounts.accounts.size).isEqualTo(1)
    }

    @Test
    fun `sync disconnects the device when the person removed every account`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        localAccounts.accounts.clear()

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.NotConnected)

        assertThat(api.disconnected).containsExactly(DEVICE_ID)
        assertThat(store.isConnected).isFalse()
        assertThat(scheduler.cancelled).isEqualTo(1)
    }

    @Test
    fun `removing an account keeps the device while other accounts remain`() = runTest {
        connected(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))
        val ana = localAccounts.uuidOf("ana@casapepe.com")

        synchronizer().onAccountRemoved(ana)

        assertThat(api.disconnected).isEmpty()
        assertThat(store.accounts.map { it.address }).containsExactly("info@casapepe.com")
        assertThat(store.isConnected).isTrue()
    }

    @Test
    fun `removing the last account disconnects the device`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        val ana = localAccounts.uuidOf("ana@casapepe.com")

        synchronizer().onAccountRemoved(ana)

        assertThat(api.disconnected).containsExactly(DEVICE_ID)
        assertThat(store.isConnected).isFalse()
        assertThat(store.authState).isNull()
        assertThat(scheduler.cancelled).isEqualTo(1)
    }

    @Test
    fun `removing an account that is not Imagina's changes nothing`() = runTest {
        connected(mailbox("ana@casapepe.com"))

        synchronizer().onAccountRemoved("another-account")

        assertThat(api.disconnected).isEmpty()
        assertThat(store.isConnected).isTrue()
    }

    private companion object {
        const val DEVICE_ID = "device-1"
        const val DEVICE_NAME = "Google Pixel 8"
        const val NOW = 1_000_000L
    }
}

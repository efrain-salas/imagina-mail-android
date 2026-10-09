package net.thunderbird.android.imagina

import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import assertk.assertThat
import assertk.assertions.containsExactly
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

/** The Firebase token of the phone, given to Imagina, and how often the accounts look for mail because of it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImaginaPushTest {
    private val api = FakeImaginaDevicesApi()
    private val store = InMemoryImaginaDeviceStore()
    private val session = FakeImaginaSession(store)
    private val localAccounts = FakeImaginaLocalAccounts()
    private val scheduler = FakeImaginaSyncScheduler()
    private val tokenSource = FakeImaginaPushTokenSource()
    private val push = ImaginaPushRegistrar(api, store, tokenSource, TestLogger())

    private fun synchronizer() = ImaginaDeviceSynchronizer(
        api = api,
        session = session,
        store = store,
        localAccounts = localAccounts,
        scheduler = scheduler,
        push = push,
        logger = TestLogger(),
        dispatcher = UnconfinedTestDispatcher(),
        clock = { NOW },
    )

    private fun registers(vararg mailboxes: ImaginaMailAccount, deviceId: String = DEVICE_ID) {
        api.registration = ImaginaDeviceRegistration(deviceId = deviceId, mailboxes = mailboxes.toList())
    }

    private suspend fun signIn() {
        synchronizer().connect(AuthorizationState("oauth-state"), "Google Pixel 8")
    }

    private fun frequencies(): List<Int> = localAccounts.checkFrequencies.values.toList()

    @Test
    fun `signing in gives Imagina the token and relaxes the checks of every account to an hour`() = runTest {
        registers(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))

        signIn()

        assertThat(api.pushTokens).isEqualTo(mapOf(DEVICE_ID to "fcm-token-1"))
        assertThat(push.isActive).isTrue()
        assertThat(frequencies()).containsExactly(60, 60)
        assertThat(store.checkFrequencyMinutes).isEqualTo(60)
        assertThat(scheduler.syncsSoon).isEqualTo(0)
    }

    @Test
    fun `without Firebase nothing is given to Imagina and the accounts keep looking every 15 minutes`() = runTest {
        tokenSource.isAvailable = false
        registers(mailbox("ana@casapepe.com"))

        signIn()

        assertThat(api.pushRegistrations).isEmpty()
        assertThat(push.isActive).isFalse()
        assertThat(frequencies()).containsExactly(15)
    }

    @Test
    fun `signing in works when Imagina cannot be asked for the push yet and the sync tries again`() = runTest {
        api.pushFailure = ImaginaNetworkException(IOException("offline"))
        registers(mailbox("ana@casapepe.com"))

        signIn()

        assertThat(store.isConnected).isTrue()
        assertThat(push.isActive).isFalse()
        assertThat(frequencies()).containsExactly(15)
        assertThat(scheduler.syncsSoon).isEqualTo(1)
    }

    @Test
    fun `when Imagina refuses the token the push stays off and nothing is retried`() = runTest {
        api.pushFailure = ImaginaApiException(status = 422, code = "invalid_token", serverMessage = null)
        registers(mailbox("ana@casapepe.com"))

        signIn()

        assertThat(push.isActive).isFalse()
        assertThat(frequencies()).containsExactly(15)
        assertThat(scheduler.syncsSoon).isEqualTo(0)
    }

    @Test
    fun `with no token yet the push waits for Firebase to give one`() = runTest {
        tokenSource.token = null
        registers(mailbox("ana@casapepe.com"))

        signIn()

        assertThat(api.pushRegistrations).isEmpty()
        assertThat(push.isActive).isFalse()
        assertThat(frequencies()).containsExactly(15)
        assertThat(scheduler.syncsSoon).isEqualTo(0)
    }

    @Test
    fun `the sync gives Imagina the token when it does not have it`() = runTest {
        tokenSource.isAvailable = false
        registers(mailbox("ana@casapepe.com"))
        signIn()
        tokenSource.isAvailable = true

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.Synced)

        assertThat(api.pushTokens).isEqualTo(mapOf(DEVICE_ID to "fcm-token-1"))
        assertThat(frequencies()).containsExactly(60)
    }

    @Test
    fun `the sync does not give the same token twice`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        api.pushRegistrations.clear()

        synchronizer().sync()
        synchronizer().sync()

        assertThat(api.pushRegistrations).isEmpty()
    }

    @Test
    fun `the sync gives Imagina the token again when Firebase changed it`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        tokenSource.token = "fcm-token-2"

        synchronizer().sync()

        assertThat(api.pushTokens).isEqualTo(mapOf(DEVICE_ID to "fcm-token-2"))
        assertThat(push.isActive).isTrue()
        assertThat(frequencies()).containsExactly(60)
    }

    @Test
    fun `a sync that cannot give the token fails to be retried and leaves the accounts as they are`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        tokenSource.isAvailable = false
        signIn()
        tokenSource.isAvailable = true
        api.pushFailure = ImaginaNetworkException(IOException("offline"))

        assertThat(synchronizer().sync()).isInstanceOf<ImaginaSyncResult.Failed>()

        assertThat(push.isActive).isFalse()
        assertThat(frequencies()).containsExactly(15)
    }

    @Test
    fun `a device Imagina disconnected is found when the token is given`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        tokenSource.token = "fcm-token-2"
        api.pushFailure = ImaginaDeviceRevokedException()

        assertThat(synchronizer().sync()).isEqualTo(ImaginaSyncResult.Revoked)

        assertThat(localAccounts.accounts).isEmpty()
        assertThat(store.isConnected).isFalse()
        assertThat(store.pushToken).isNull()
        assertThat(store.pushRegisteredDeviceId).isNull()
        assertThat(push.isActive).isFalse()
    }

    @Test
    fun `if Firebase goes away the checks go back to every 15 minutes`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        tokenSource.isAvailable = false

        synchronizer().sync()

        assertThat(push.isActive).isFalse()
        assertThat(frequencies()).containsExactly(15)
        assertThat(store.checkFrequencyMinutes).isEqualTo(15)
    }

    @Test
    fun `a mailbox that Imagina adds starts out with the checks the others have`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        api.statusResult = { status(changed = true) }
        api.refreshResult =
            { ImaginaDeviceRefresh(added = listOf(mailbox("info@casapepe.com")), removed = emptyList()) }

        synchronizer().sync()

        assertThat(frequencies()).containsExactly(60, 60)
    }

    @Test
    fun `a frequency the person chose is left alone while push does not change`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        val account = localAccounts.uuidOf("ana@casapepe.com")
        localAccounts.setCheckFrequency(account, 30)

        synchronizer().sync()

        assertThat(localAccounts.checkFrequencies.getValue(account)).isEqualTo(30)
    }

    @Test
    fun `signing in again with a new device gives Imagina the token for the new device`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        api.statusResult = { status(revoked = true) }
        registers(mailbox("ana@casapepe.com"), deviceId = "device-2")

        signIn()

        assertThat(api.pushTokens).isEqualTo(mapOf(DEVICE_ID to "fcm-token-1", "device-2" to "fcm-token-1"))
        assertThat(push.isActive).isTrue()
        assertThat(store.pushRegisteredDeviceId).isEqualTo("device-2")
    }

    @Test
    fun `removing the last account asks Imagina to forget the token and forgets it`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        val account = localAccounts.uuidOf("ana@casapepe.com")

        synchronizer().onAccountRemoved(account)

        assertThat(api.pushForgotten).containsExactly(DEVICE_ID)
        assertThat(api.pushTokens).isEmpty()
        assertThat(api.disconnected).containsExactly(DEVICE_ID)
        assertThat(store.pushToken).isNull()
        assertThat(store.pushRegisteredDeviceId).isNull()
        assertThat(store.checkFrequencyMinutes).isEqualTo(0)
    }

    @Test
    fun `the device is disconnected even if Imagina cannot forget the token`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        api.pushFailure = ImaginaNetworkException(IOException("offline"))

        synchronizer().onAccountRemoved(localAccounts.uuidOf("ana@casapepe.com"))

        assertThat(api.disconnected).containsExactly(DEVICE_ID)
        assertThat(store.isConnected).isFalse()
        assertThat(store.pushToken).isNull()
    }

    @Test
    fun `a device Imagina disconnected is forgotten without asking Imagina to forget the token`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        api.statusResult = { status(revoked = true) }

        synchronizer().sync()

        assertThat(api.pushForgotten).isEmpty()
        assertThat(store.pushToken).isNull()
        assertThat(store.pushRegisteredDeviceId).isNull()
    }

    @Test
    fun `a new token is given to Imagina at once`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        tokenSource.token = "fcm-token-2"

        push.onNewToken("fcm-token-2")
        assertThat(push.isActive).isFalse()
        synchronizer().onPushTokenChanged()

        assertThat(api.pushTokens).isEqualTo(mapOf(DEVICE_ID to "fcm-token-2"))
        assertThat(push.isActive).isTrue()
    }

    @Test
    fun `a new token that Imagina cannot get yet is left to the sync`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        tokenSource.token = "fcm-token-2"
        api.pushFailure = ImaginaNetworkException(IOException("offline"))

        push.onNewToken("fcm-token-2")
        synchronizer().onPushTokenChanged()

        assertThat(push.isActive).isFalse()
        assertThat(push.needsRegistration).isTrue()
        assertThat(scheduler.syncsSoon).isEqualTo(1)
    }

    @Test
    fun `a new token on a phone that is not connected is ignored`() = runTest {
        push.onNewToken("fcm-token-2")
        synchronizer().onPushTokenChanged()

        assertThat(store.pushToken).isNull()
        assertThat(api.pushRegistrations).isEmpty()
    }

    @Test
    fun `a new token for a device Imagina disconnected removes its accounts`() = runTest {
        registers(mailbox("ana@casapepe.com"))
        signIn()
        tokenSource.token = "fcm-token-2"
        api.pushFailure = ImaginaDeviceRevokedException()

        push.onNewToken("fcm-token-2")
        synchronizer().onPushTokenChanged()

        assertThat(localAccounts.accounts).isEmpty()
        assertThat(store.isConnected).isFalse()
    }

    @Test
    fun `a push that is not registered is wanted only when Firebase is there and the phone is connected`() = runTest {
        assertThat(push.needsRegistration).isFalse()

        registers(mailbox("ana@casapepe.com"))
        tokenSource.isAvailable = false
        signIn()
        assertThat(push.needsRegistration).isFalse()

        tokenSource.isAvailable = true
        assertThat(push.needsRegistration).isTrue()

        synchronizer().sync()
        assertThat(push.needsRegistration).isFalse()
    }

    private companion object {
        const val DEVICE_ID = "device-1"
        const val NOW = 1_000_000L
    }
}

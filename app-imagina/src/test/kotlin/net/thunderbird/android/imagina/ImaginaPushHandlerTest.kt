package net.thunderbird.android.imagina

import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.testing.TestLogger

@OptIn(ExperimentalCoroutinesApi::class)
class ImaginaPushHandlerTest {
    private val api = FakeImaginaDevicesApi()
    private val store = InMemoryImaginaDeviceStore()
    private val session = FakeImaginaSession(store)
    private val localAccounts = FakeImaginaLocalAccounts()
    private val scheduler = FakeImaginaSyncScheduler()
    private val tokenSource = FakeImaginaPushTokenSource()
    private val push = ImaginaPushRegistrar(api, store, tokenSource, TestLogger())
    private var now = LAST_SYNC + 60_000L

    private val synchronizer = ImaginaDeviceSynchronizer(
        api = api,
        session = session,
        store = store,
        localAccounts = localAccounts,
        scheduler = scheduler,
        push = push,
        logger = TestLogger(),
        dispatcher = UnconfinedTestDispatcher(),
        clock = { LAST_SYNC },
    )

    private val handler = ImaginaPushHandler(
        store = store,
        localAccounts = localAccounts,
        push = push,
        synchronizer = synchronizer,
        scheduler = scheduler,
        logger = TestLogger(),
        ioDispatcher = UnconfinedTestDispatcher(),
        clock = { now },
    )

    private suspend fun connected(vararg mailboxes: ImaginaMailAccount) {
        api.registration = ImaginaDeviceRegistration(deviceId = DEVICE_ID, mailboxes = mailboxes.toList())
        synchronizer.connect(AuthorizationState("oauth-state"), "Google Pixel 8")
    }

    private fun newMail(address: String, deviceId: String = DEVICE_ID) = mapOf(
        "type" to "new_mail",
        "mailbox" to ImaginaPushMessage.mailboxHash(deviceId, address),
    )

    @Test
    fun `a push checks the inbox of the account whose mailbox it names`() = runTest {
        connected(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))

        handler.onMessage(newMail("info@casapepe.com"))

        assertThat(scheduler.inboxChecks).containsExactly(localAccounts.uuidOf("info@casapepe.com"))
    }

    @Test
    fun `the case of the address does not matter`() = runTest {
        connected(mailbox("Ana@CasaPepe.com"))

        handler.onMessage(newMail("ana@casapepe.com"))

        assertThat(scheduler.inboxChecks).containsExactly(localAccounts.uuidOf("Ana@CasaPepe.com"))
    }

    @Test
    fun `a push for a mailbox this phone has no account for checks nothing`() = runTest {
        connected(mailbox("ana@casapepe.com"))

        handler.onMessage(newMail("other@casapepe.com"))

        assertThat(scheduler.inboxChecks).isEmpty()
    }

    @Test
    fun `a mailbox hash made for another device does not match`() = runTest {
        connected(mailbox("ana@casapepe.com"))

        handler.onMessage(newMail("ana@casapepe.com", deviceId = "device-2"))

        assertThat(scheduler.inboxChecks).isEmpty()
    }

    @Test
    fun `an unknown mailbox asks for a sync with Imagina when the last one is not recent`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        scheduler.syncsSoon = 0
        now = LAST_SYNC + 20 * 60_000L

        handler.onMessage(newMail("other@casapepe.com"))

        assertThat(scheduler.syncsSoon).isEqualTo(1)
    }

    @Test
    fun `an unknown mailbox does not ask for a sync with Imagina when there was one just now`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        scheduler.syncsSoon = 0

        handler.onMessage(newMail("other@casapepe.com"))

        assertThat(scheduler.syncsSoon).isEqualTo(0)
    }

    @Test
    fun `a push for an account the person removed checks nothing`() = runTest {
        connected(mailbox("ana@casapepe.com"), mailbox("info@casapepe.com"))
        localAccounts.accounts.remove(localAccounts.uuidOf("info@casapepe.com"))

        handler.onMessage(newMail("info@casapepe.com"))

        assertThat(scheduler.inboxChecks).isEmpty()
    }

    @Test
    fun `messages that are not new mail pushes are ignored`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        val mailboxHash = ImaginaPushMessage.mailboxHash(DEVICE_ID, "ana@casapepe.com")

        handler.onMessage(mapOf("type" to "something_else", "mailbox" to mailboxHash))
        handler.onMessage(mapOf("mailbox" to mailboxHash))
        handler.onMessage(mapOf("type" to "new_mail"))
        handler.onMessage(emptyMap())

        assertThat(scheduler.inboxChecks).isEmpty()
    }

    @Test
    fun `a push on a phone that is not connected is ignored`() = runTest {
        handler.onMessage(newMail("ana@casapepe.com"))

        assertThat(scheduler.inboxChecks).isEmpty()
        assertThat(scheduler.syncsSoon).isEqualTo(0)
    }

    @Test
    fun `a new token is given to Imagina`() = runTest {
        connected(mailbox("ana@casapepe.com"))
        tokenSource.token = "fcm-token-2"

        handler.onNewToken("fcm-token-2")

        assertThat(api.pushTokens).isEqualTo(mapOf(DEVICE_ID to "fcm-token-2"))
        assertThat(push.isActive).isTrue()
    }

    private companion object {
        const val DEVICE_ID = "device-1"
        const val LAST_SYNC = 1_000_000L
    }
}

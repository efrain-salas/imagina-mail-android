package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ImaginaInboxCheckerTest {
    private val inbox = RecordingInbox()
    private var now = 1_000L
    private val checker = ImaginaInboxChecker(inbox, UnconfinedTestDispatcher(), clock = { now })

    private class RecordingInbox : ImaginaInbox {
        val synced = mutableListOf<String>()

        override fun sync(accountUuid: String): Boolean {
            synced += accountUuid
            return true
        }
    }

    @Test
    fun `a push checks the inbox of its account`() = runTest {
        checker.check("account-1", requestedAt = 1_000L)

        assertThat(inbox.synced).containsExactly("account-1")
    }

    @Test
    fun `a push that arrives after the last check began gets another check`() = runTest {
        checker.check("account-1", requestedAt = 1_000L)
        now = 2_000L
        checker.check("account-1", requestedAt = 1_500L)

        assertThat(inbox.synced).containsExactly("account-1", "account-1")
    }

    @Test
    fun `a push that a later check already covers is not checked again`() = runTest {
        now = 3_000L
        checker.check("account-1", requestedAt = 2_000L)
        // The push below was received at 2,500, before that check began: it has seen its mail
        checker.check("account-1", requestedAt = 2_500L)

        assertThat(inbox.synced).containsExactly("account-1")
    }

    @Test
    fun `each account has its own checks`() = runTest {
        checker.check("account-1", requestedAt = 1_000L)
        checker.check("account-2", requestedAt = 1_000L)

        assertThat(inbox.synced).containsExactly("account-1", "account-2")
    }
}

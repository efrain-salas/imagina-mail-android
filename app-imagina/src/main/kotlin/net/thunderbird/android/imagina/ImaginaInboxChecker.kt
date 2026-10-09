package net.thunderbird.android.imagina

import android.content.ContentResolver
import app.k9mail.legacy.mailstore.FolderRepository
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessagingController
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.preference.BackgroundOps
import net.thunderbird.core.preference.GeneralSettingsManager

/** The inbox of one of the phone's accounts, as far as a push from Imagina is concerned. */
interface ImaginaInbox {
    /**
     * Fetches the new mail of the account's inbox, showing the usual new-mail notification, and returns when
     * it is done. Blocks the thread.
     *
     * @return `false` when nothing was done: the account or its inbox are not there (yet) or the person turned
     *   background sync off.
     */
    fun sync(accountUuid: String): Boolean
}

/**
 * Does what Thunderbird's own push does when its connection hears a new message: `synchronizeMailboxBlocking` on
 * the inbox, which notifies for what is new. The person's «never sync in the background» setting is respected, as
 * the periodic check does.
 */
class ThunderbirdImaginaInbox(
    private val preferences: Preferences,
    private val messagingController: MessagingController,
    private val folderRepository: FolderRepository,
    private val generalSettingsManager: GeneralSettingsManager,
    private val logger: Logger,
) : ImaginaInbox {

    override fun sync(accountUuid: String): Boolean {
        val account = preferences.getAccount(accountUuid)
        val inboxServerId = account?.let(::inboxServerId)
        if (account == null || inboxServerId == null) {
            logger.warn(TAG) { "No inbox to check for an account Imagina sent a push for" }
            return false
        }

        val mayCheck = !isBackgroundSyncDisabled() && !account.incomingServerSettings.isMissingCredentials
        if (mayCheck) {
            messagingController.synchronizeMailboxBlocking(account, inboxServerId)
        }

        return mayCheck
    }

    private fun inboxServerId(account: LegacyAccountDto): String? =
        account.inboxFolderId?.let { folderRepository.getFolderServerId(account.id, it) }

    private fun isBackgroundSyncDisabled(): Boolean {
        return when (generalSettingsManager.getConfig().network.backgroundOps) {
            BackgroundOps.NEVER -> true
            BackgroundOps.ALWAYS -> false
            BackgroundOps.WHEN_CHECKED_AUTO_SYNC -> !ContentResolver.getMasterSyncAutomatically()
        }
    }

    private companion object {
        const val TAG = "ThunderbirdImaginaInbox"
    }
}

/**
 * Checks inboxes after a push, one at a time per account. A push says «mail arrived» and the check that
 * comes after reads the whole inbox, so when many pushes pile up (a burst of messages) the checks that wait
 * are merged: a check that began after a push was received has already seen its mail.
 */
class ImaginaInboxChecker(
    private val inbox: ImaginaInbox,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val lastStartedAt = ConcurrentHashMap<String, Long>()

    /**
     * Checks the inbox of [accountUuid] unless a check that began at or after [requestedAt] (when the push was
     * received) already did.
     */
    suspend fun check(accountUuid: String, requestedAt: Long) {
        locks.getOrPut(accountUuid) { Mutex() }.withLock {
            val startedAt = lastStartedAt[accountUuid]
            if (startedAt == null || startedAt < requestedAt) {
                lastStartedAt[accountUuid] = clock()
                withContext(ioDispatcher) { inbox.sync(accountUuid) }
            }
        }
    }
}

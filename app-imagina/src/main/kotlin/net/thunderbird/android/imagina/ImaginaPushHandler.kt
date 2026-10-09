package net.thunderbird.android.imagina

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.thunderbird.core.logging.Logger

/**
 * What Imagina Mail does with what Firebase delivers ([ImaginaMessagingService] only passes it on):
 *
 * - a `new_mail` push: finds the account of that mailbox and fetches its inbox at once, so Thunderbird shows its
 *   normal new-mail notification;
 * - a new token: gives it to Imagina.
 */
class ImaginaPushHandler(
    private val store: ImaginaDeviceStore,
    private val localAccounts: ImaginaLocalAccounts,
    private val push: ImaginaPushRegistrar,
    private val synchronizer: ImaginaDeviceSynchronizer,
    private val scheduler: ImaginaSyncScheduler,
    private val logger: Logger,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Firebase gave this phone a new token. */
    fun onNewToken(token: String) {
        push.onNewToken(token)
        scope.launch { synchronizer.onPushTokenChanged() }
    }

    /** A data message arrived from Firebase. Anything that is not a `new_mail` push for this phone is ignored. */
    fun onMessage(data: Map<String, String>) {
        val mailbox = ImaginaPushMessage.newMailMailbox(data)
        val deviceId = store.deviceId
        if (mailbox == null || deviceId == null) return

        val account = store.accounts.firstOrNull { account ->
            localAccounts.exists(account.uuid) && ImaginaPushMessage.mailboxHash(deviceId, account.address) == mailbox
        }

        if (account != null) {
            scheduler.checkInbox(account.uuid)
        } else {
            logger.info(TAG) { "Push for a mailbox this phone has no account for" }
            syncDevicesIfNotRecently()
        }
    }

    /** A mailbox that is not here may be one Imagina just added: look at the device, at most every few minutes. */
    private fun syncDevicesIfNotRecently() {
        if (clock() - store.lastSyncAt > MIN_SYNC_AGE_MILLIS) {
            scheduler.syncSoon()
        }
    }

    private companion object {
        const val TAG = "ImaginaPushHandler"
        const val MIN_SYNC_AGE_MILLIS = 15 * 60 * 1000L
    }
}

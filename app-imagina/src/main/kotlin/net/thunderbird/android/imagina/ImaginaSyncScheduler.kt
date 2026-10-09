package net.thunderbird.android.imagina

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fsck.k9.notification.BackgroundWorkNotificationController
import java.util.concurrent.TimeUnit
import net.thunderbird.android.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * When the mailboxes are compared with Imagina's (C7.3): every few hours, when the app starts and
 * after signing in. Tokens last 15 minutes (access) and 7 days (refresh, rotating), so a sync at
 * least every few hours keeps the sign-in alive. Also when an inbox is checked at once because
 * Imagina said, through Firebase, that mail arrived (C12).
 */
interface ImaginaSyncScheduler {
    /** Keeps a periodic sync going. */
    fun schedule()

    /** Syncs as soon as there is a network. */
    fun syncSoon()

    /** Fetches the new mail of the account's inbox as soon as there is a network, as expedited work. */
    fun checkInbox(accountUuid: String)

    /** The phone is not connected to Imagina any more. */
    fun cancel()
}

class WorkManagerImaginaSyncScheduler(private val context: Context) : ImaginaSyncScheduler {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<ImaginaSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun syncSoon() {
        val request = OneTimeWorkRequestBuilder<ImaginaSyncWorker>()
            .setConstraints(CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun checkInbox(accountUuid: String) {
        // Not unique: a push that arrives while another check runs must get a check of its own (the inbox checker
        // merges the ones that wait)
        val request = OneTimeWorkRequestBuilder<ImaginaMailCheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(CONSTRAINTS)
            .setInputData(
                workDataOf(
                    ImaginaMailCheckWorker.KEY_ACCOUNT_UUID to accountUuid,
                    ImaginaMailCheckWorker.KEY_REQUESTED_AT to System.currentTimeMillis(),
                ),
            )
            .build()

        workManager.enqueue(request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "imagina-device-sync"
        const val ONE_TIME_WORK_NAME = "imagina-device-sync-now"
        const val SYNC_INTERVAL_HOURS = 6L
        const val BACKOFF_MINUTES = 15L
        val CONSTRAINTS: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

/**
 * Runs [ImaginaDeviceSynchronizer.sync] in the background. WorkManager creates it by reflection:
 * Thunderbird's `K9WorkerFactory` only builds the workers of `com.fsck.k9` and leaves the others
 * to WorkManager's default factory.
 */
class ImaginaSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {
    private val synchronizer: ImaginaDeviceSynchronizer by inject()

    override suspend fun doWork(): Result {
        return when (synchronizer.sync()) {
            is ImaginaSyncResult.Failed -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()

            ImaginaSyncResult.NotConnected,
            ImaginaSyncResult.Synced,
            ImaginaSyncResult.Revoked,
            ImaginaSyncResult.SignInRequired,
            -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * Fetches the new mail of one account's inbox after a push from Imagina. Expedited, so it starts at once from
 * the background (a high-priority Firebase message allows it); before Android 12 expedited work is a foreground
 * service and shows Thunderbird's «background work» notification while it runs.
 */
class ImaginaMailCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {
    private val inboxChecker: ImaginaInboxChecker by inject()
    private val notifications: BackgroundWorkNotificationController by inject()

    override suspend fun doWork(): Result {
        val accountUuid = inputData.getString(KEY_ACCOUNT_UUID) ?: return Result.failure()

        inboxChecker.check(accountUuid, requestedAt = inputData.getLong(KEY_REQUESTED_AT, 0L))

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val text = applicationContext.getString(R.string.imagina_checking_mail)

        return ForegroundInfo(notifications.notificationId, notifications.createNotification(text))
    }

    companion object {
        const val KEY_ACCOUNT_UUID = "accountUuid"
        const val KEY_REQUESTED_AT = "requestedAt"
    }
}

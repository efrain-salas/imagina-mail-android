package net.thunderbird.android.imagina

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * When the mailboxes are compared with Imagina's (C7.3): every few hours, when the app starts and
 * after signing in. Tokens last 15 minutes (access) and 7 days (refresh, rotating), so a sync at
 * least every few hours keeps the sign-in alive.
 */
interface ImaginaSyncScheduler {
    /** Keeps a periodic sync going. */
    fun schedule()

    /** Syncs as soon as there is a network. */
    fun syncSoon()

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

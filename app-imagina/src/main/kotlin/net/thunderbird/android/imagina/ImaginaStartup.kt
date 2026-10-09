package net.thunderbird.android.imagina

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import com.fsck.k9.Preferences
import com.fsck.k9.activity.MessageHomeActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.thunderbird.core.android.account.AccountRemovedListener

/**
 * What Imagina Mail starts with the app (`ThunderbirdApp.onCreate`):
 *
 * - the sync with Imagina's devices API, now and every few hours (C7.3),
 * - the offer to «Entrar con Imagina» again when the last sync lost its sign-in,
 * - the disconnection of the phone when its last Imagina account is removed.
 */
class ImaginaStartup(
    private val application: Application,
    private val store: ImaginaDeviceStore,
    private val synchronizer: ImaginaDeviceSynchronizer,
    private val scheduler: ImaginaSyncScheduler,
    private val preferences: Preferences,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    fun start() {
        preferences.addAccountRemovedListener(
            AccountRemovedListener { accountId ->
                scope.launch { synchronizer.onAccountRemoved(accountId.toString()) }
            },
        )
        application.registerActivityLifecycleCallbacks(ReconnectOffer(store))

        if (store.isConnected) {
            scheduler.schedule()
            if (System.currentTimeMillis() - store.lastSyncAt > MIN_SYNC_AGE_ON_START_MILLIS) {
                scheduler.syncSoon()
            }
        }
    }

    /**
     * When the sync can no longer renew the sign-in (the refresh token is gone or refused) mail keeps
     * working; the next time the app opens its inbox, once per run, «Entrar con Imagina» is offered
     * to resync. «Ahora no» leaves it for the next start.
     */
    private class ReconnectOffer(private val store: ImaginaDeviceStore) : Application.ActivityLifecycleCallbacks {
        private var offered = false

        override fun onActivityResumed(activity: Activity) {
            if (!offered && activity is MessageHomeActivity && store.needsSignIn) {
                offered = true
                FeatureLauncherActivity.launch(activity, FeatureLauncherTarget.AccountSetup)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val MIN_SYNC_AGE_ON_START_MILLIS = 15 * 60 * 1000L
    }
}

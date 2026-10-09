package net.thunderbird.android.imagina

import android.os.Build
import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.thunderbird.core.logging.Logger

sealed interface ImaginaSyncResult {
    /** This phone is not connected to Imagina. */
    data object NotConnected : ImaginaSyncResult

    /** The accounts match Imagina's mailboxes. */
    data object Synced : ImaginaSyncResult

    /** The person disconnected this phone from Imagina: its accounts were removed. */
    data object Revoked : ImaginaSyncResult

    /** The sign-in no longer works: the next app start offers «Entrar con Imagina». Mail keeps working. */
    data object SignInRequired : ImaginaSyncResult

    /** Imagina could not be asked just now; it should be tried again later. */
    data class Failed(val error: ImaginaException) : ImaginaSyncResult
}

/**
 * Keeps this phone's mail accounts equal to the person's mailboxes in Imagina, through the devices
 * API: one account per mailbox, with the credential Imagina made for this device.
 *
 * A device is registered only when the phone has none or Imagina revoked it; signing in again with a
 * device that is still valid just brings the accounts up to date.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class ImaginaDeviceSynchronizer(
    private val api: ImaginaDevicesApi,
    private val session: ImaginaSession,
    private val store: ImaginaDeviceStore,
    private val localAccounts: ImaginaLocalAccounts,
    private val scheduler: ImaginaSyncScheduler,
    private val logger: Logger,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /**
     * The person has just signed in with Imagina ([authorizationState]): registers this phone if it
     * is not connected (or Imagina revoked it) and sets the accounts of their mailboxes up.
     *
     * @return the uuid of the account to show first.
     * @throws ImaginaException when it could not be done; nothing is left half-registered.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun connect(
        authorizationState: AuthorizationState,
        deviceName: String = defaultDeviceName(),
    ): String {
        return withContext(dispatcher) {
            mutex.withLock {
                try {
                    connectLocked(authorizationState, deviceName)
                } catch (error: Throwable) {
                    keepStateConsistentAfterFailedConnect()
                    throw error
                }
            }
        }
    }

    private suspend fun connectLocked(authorizationState: AuthorizationState, deviceName: String): String {
        pruneAccountsRemovedByThePerson()
        session.signedIn(authorizationState)
        val accessToken = session.accessToken()

        val deviceId = store.deviceId
        val connected = deviceId != null && updateKnownDevice(accessToken, deviceId)
        if (!connected) {
            registerDevice(accessToken, deviceName)
        }

        store.lastSyncAt = clock()
        store.needsSignIn = false
        scheduler.schedule()

        return store.accounts.firstOrNull { localAccounts.exists(it.uuid) }?.uuid
            ?: throw ImaginaNoMailboxesException()
    }

    /** Nothing to keep up to date: forget the sign-in too. Accounts without a valid device: offer to sign in. */
    private fun keepStateConsistentAfterFailedConnect() {
        when {
            store.accounts.isEmpty() -> store.clear()
            !store.isConnected -> store.needsSignIn = true
        }
    }

    /**
     * Compares the accounts with Imagina's mailboxes (C7.3): adds the accounts of new mailboxes,
     * removes those of mailboxes taken away and, when the person disconnected this phone from
     * Imagina, removes the Imagina accounts.
     */
    suspend fun sync(): ImaginaSyncResult {
        return withContext(dispatcher) {
            mutex.withLock {
                val deviceId = store.deviceId
                if (deviceId == null || !pruneAccountsRemovedByThePerson()) {
                    ImaginaSyncResult.NotConnected
                } else {
                    syncDevice(deviceId)
                }
            }
        }
    }

    private suspend fun syncDevice(deviceId: String): ImaginaSyncResult {
        return try {
            val accessToken = session.accessToken()
            val status = api.getDevice(accessToken, deviceId)
            if (status.revoked) {
                removeAccountsOfRevokedDevice()
                ImaginaSyncResult.Revoked
            } else {
                if (status.changed) {
                    applyRefresh(accessToken, deviceId)
                }

                store.lastSyncAt = clock()
                store.needsSignIn = false
                ImaginaSyncResult.Synced
            }
        } catch (_: ImaginaDeviceRevokedException) {
            removeAccountsOfRevokedDevice()
            ImaginaSyncResult.Revoked
        } catch (_: ImaginaSignInRequiredException) {
            store.needsSignIn = true
            ImaginaSyncResult.SignInRequired
        } catch (error: ImaginaApiException) {
            if (error.status in SIGN_IN_REQUIRED_STATUSES) {
                store.needsSignIn = true
                ImaginaSyncResult.SignInRequired
            } else {
                ImaginaSyncResult.Failed(error)
            }
        } catch (error: ImaginaException) {
            ImaginaSyncResult.Failed(error)
        }
    }

    /** The person removed an account from the app: when it was the last one of Imagina's, disconnect the phone. */
    suspend fun onAccountRemoved(accountUuid: String) {
        withContext(dispatcher) {
            mutex.withLock {
                val remaining = store.accounts.filterNot { it.uuid == accountUuid }
                if (remaining.size != store.accounts.size) {
                    store.accounts = remaining
                    if (remaining.isEmpty()) {
                        disconnectDevice()
                    }
                }
            }
        }
    }

    /**
     * Drops the accounts the person removed in the app. Without any account left there is nothing to
     * keep up to date, so the device is disconnected.
     *
     * @return whether the phone is still connected to Imagina.
     */
    private suspend fun pruneAccountsRemovedByThePerson(): Boolean {
        if (!store.isConnected) return false

        val existing = store.accounts.filter { localAccounts.exists(it.uuid) }
        if (existing.size != store.accounts.size) {
            store.accounts = existing
        }
        if (existing.isEmpty()) {
            disconnectDevice()
        }

        return existing.isNotEmpty()
    }

    /** @return `false` when Imagina does not know the device (any more) and a new one has to be registered. */
    private suspend fun updateKnownDevice(accessToken: String, deviceId: String): Boolean {
        val status = try {
            api.getDevice(accessToken, deviceId)
        } catch (error: ImaginaApiException) {
            if (error.status != HTTP_NOT_FOUND) throw error
            null
        }

        val isValid = when {
            status == null || status.revoked -> false

            status.changed -> try {
                applyRefresh(accessToken, deviceId)
                true
            } catch (_: ImaginaDeviceRevokedException) {
                false
            }

            else -> true
        }
        if (!isValid) {
            store.deviceId = null
        }

        return isValid
    }

    // The catch only cleans up and rethrows, cancellation included
    @Suppress("TooGenericExceptionCaught", "SuspendFunSwallowedCancellation")
    private suspend fun registerDevice(accessToken: String, deviceName: String) {
        val registration = api.registerDevice(accessToken, deviceName)
        if (registration.mailboxes.isEmpty()) {
            bestEffort { api.disconnectDevice(accessToken, registration.deviceId) }
            throw ImaginaNoMailboxesException()
        }

        store.deviceId = registration.deviceId
        val createdAccounts = mutableListOf<ImaginaManagedAccount>()
        try {
            registration.mailboxes.forEach { mailbox -> upsert(mailbox, registration.mailboxes, createdAccounts) }

            val listed = registration.mailboxes.map { it.address.lowercase() }.toSet()
            store.accounts
                .filter { it.address.lowercase() !in listed }
                .forEach { removeAccount(it) }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                bestEffort { api.disconnectDevice(accessToken, registration.deviceId) }
                createdAccounts.forEach { removeAccount(it) }
                store.deviceId = null
            }
            throw error
        }
    }

    // The catch only cleans up and rethrows, cancellation included
    @Suppress("TooGenericExceptionCaught", "SuspendFunSwallowedCancellation")
    private suspend fun applyRefresh(accessToken: String, deviceId: String) {
        val refresh = api.refreshDevice(accessToken, deviceId)

        refresh.removed.forEach { address ->
            store.accounts.firstOrNull { it.address.equals(address, ignoreCase = true) }?.let { removeAccount(it) }
        }

        try {
            refresh.added.forEach { mailbox -> upsert(mailbox, refresh.added, createdAccounts = null) }
        } catch (error: Exception) {
            // Imagina counts these mailboxes as delivered: only signing in again (a new device) brings them
            withContext(NonCancellable) {
                bestEffort { api.disconnectDevice(accessToken, deviceId) }
                store.deviceId = null
                store.needsSignIn = true
            }
            throw error
        }
    }

    /** Creates the account of [mailbox], or updates the credential of the one it already has. */
    private suspend fun upsert(
        mailbox: ImaginaMailAccount,
        batch: List<ImaginaMailAccount>,
        createdAccounts: MutableList<ImaginaManagedAccount>?,
    ) {
        val known = store.accounts.firstOrNull { it.address.equals(mailbox.address, ignoreCase = true) }
        if (known != null && localAccounts.exists(known.uuid)) {
            localAccounts.updateCredentials(known.uuid, mailbox)
            store.accounts = store.accounts.map { if (it == known) known.copy(company = mailbox.company) else it }
            return
        }

        val uuid = localAccounts.create(mailbox, accountName(mailbox, batch))
        val account = ImaginaManagedAccount(address = mailbox.address, uuid = uuid, company = mailbox.company)
        store.accounts = store.accounts.filterNot { it.address.equals(mailbox.address, ignoreCase = true) } + account
        createdAccounts?.add(account)
    }

    /**
     * The address, and the company too when the person has mailboxes of several companies
     * (`info@casapepe.com · Casa Pepe`).
     */
    private fun accountName(mailbox: ImaginaMailAccount, batch: List<ImaginaMailAccount>): String {
        val companies = (store.accounts.map { it.company } + batch.map { it.company })
            .filter { it.isNotBlank() }
            .toSet()

        return if (companies.size > 1 && mailbox.company.isNotBlank()) {
            "${mailbox.address} · ${mailbox.company}"
        } else {
            mailbox.address
        }
    }

    private fun removeAccount(account: ImaginaManagedAccount) {
        store.accounts = store.accounts.filterNot { it.uuid == account.uuid }
        localAccounts.remove(account.uuid)
    }

    private fun removeAccountsOfRevokedDevice() {
        logger.info(TAG) { "Imagina disconnected this device: removing its accounts" }
        store.accounts.forEach { localAccounts.remove(it.uuid) }
        store.clear()
        scheduler.cancel()
    }

    /** Best effort: the device's credentials are cut by Imagina, and also expire if the call fails. */
    private suspend fun disconnectDevice() {
        val deviceId = store.deviceId
        if (deviceId != null) {
            bestEffort { api.disconnectDevice(session.accessToken(), deviceId) }
        }
        store.clear()
        scheduler.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn(TAG, error) { "Best-effort call to Imagina failed" }
        }
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()

        return when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }.trim().ifEmpty { "Android" }
    }

    private companion object {
        const val TAG = "ImaginaDeviceSynchronizer"
        const val HTTP_NOT_FOUND = 404
        val SIGN_IN_REQUIRED_STATUSES = setOf(401, 403, HTTP_NOT_FOUND)
    }
}

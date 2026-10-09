package net.thunderbird.android.imagina

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * What Imagina Mail remembers about its link with Imagina: the id of this device, the OAuth state
 * (access and refresh token, as AppAuth serialises it) and which mail accounts it made, one per
 * mailbox.
 *
 * The credentials of the mailboxes live in Thunderbird's account storage like those of any account.
 */
interface ImaginaDeviceStore {
    /** The id Imagina gave this phone, or `null` when it is not connected. */
    var deviceId: String?

    /** AppAuth's `AuthState` as JSON. */
    var authState: String?

    /** The sign-in no longer works: the next app start offers «Entrar con Imagina» to resync. */
    var needsSignIn: Boolean

    /** When the mailboxes were last compared with Imagina's, in milliseconds. */
    var lastSyncAt: Long

    /** The mail accounts made for Imagina's mailboxes. */
    var accounts: List<ImaginaManagedAccount>

    val isConnected: Boolean
        get() = deviceId != null

    /** Forgets the device, the sign-in and the accounts list (the accounts themselves are not touched). */
    fun clear()
}

/** A mail account Imagina Mail created for one of Imagina's mailboxes. */
data class ImaginaManagedAccount(val address: String, val uuid: String, val company: String)

/**
 * Kept in the app's own private storage (a SharedPreferences file of its own, not backed up: see
 * `allowBackup` in app-common).
 */
class SharedPreferencesImaginaDeviceStore(context: Context) : ImaginaDeviceStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var deviceId: String?
        get() = preferences.getString(KEY_DEVICE_ID, null)
        set(value) = preferences.edit(commit = true) { putString(KEY_DEVICE_ID, value) }

    override var authState: String?
        get() = preferences.getString(KEY_AUTH_STATE, null)
        set(value) = preferences.edit(commit = true) { putString(KEY_AUTH_STATE, value) }

    override var needsSignIn: Boolean
        get() = preferences.getBoolean(KEY_NEEDS_SIGN_IN, false)
        set(value) = preferences.edit(commit = true) { putBoolean(KEY_NEEDS_SIGN_IN, value) }

    override var lastSyncAt: Long
        get() = preferences.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = preferences.edit(commit = true) { putLong(KEY_LAST_SYNC_AT, value) }

    override var accounts: List<ImaginaManagedAccount>
        get() = decodeAccounts(preferences.getString(KEY_ACCOUNTS, null))
        set(value) = preferences.edit(commit = true) { putString(KEY_ACCOUNTS, encodeAccounts(value)) }

    override fun clear() {
        preferences.edit(commit = true) { clear() }
    }

    private companion object {
        const val PREFERENCES_NAME = "imagina_device"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_AUTH_STATE = "auth_state"
        const val KEY_NEEDS_SIGN_IN = "needs_sign_in"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_ACCOUNTS = "accounts"
    }
}

internal fun encodeAccounts(accounts: List<ImaginaManagedAccount>): String {
    val array: JsonArray = buildJsonArray {
        accounts.forEach { account ->
            add(
                buildJsonObject {
                    put(key = "address", value = account.address)
                    put(key = "uuid", value = account.uuid)
                    put(key = "company", value = account.company)
                },
            )
        }
    }
    return array.toString()
}

@Suppress("TooGenericExceptionCaught")
internal fun decodeAccounts(json: String?): List<ImaginaManagedAccount> {
    if (json.isNullOrEmpty()) return emptyList()

    return try {
        Json.parseToJsonElement(json).jsonArray.map { element ->
            val account: JsonObject = element.jsonObject
            ImaginaManagedAccount(
                address = account.getValue("address").jsonPrimitive.content,
                uuid = account.getValue("uuid").jsonPrimitive.content,
                company = account["company"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

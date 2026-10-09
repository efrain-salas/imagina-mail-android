package net.thunderbird.android.imagina

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Imagina's devices API (`/api/v1/cloud/devices`): registers this phone, tells whether the person's
 * mailboxes changed and hands out the credential of each mailbox for this device.
 */
interface ImaginaDevicesApi {
    /** Registers this phone. Refusals: 403 `no_seat`, 409 `not_ready`, 400 `not_supported`, 503 `unavailable`. */
    suspend fun registerDevice(accessToken: String, name: String): ImaginaDeviceRegistration

    suspend fun getDevice(accessToken: String, deviceId: String): ImaginaDeviceStatus

    /**
     * Delivers the mailboxes added since the last time and the addresses removed.
     * Throws [ImaginaDeviceRevokedException] when the device was disconnected.
     */
    suspend fun refreshDevice(accessToken: String, deviceId: String): ImaginaDeviceRefresh

    /** Disconnects the device and cuts all its credentials. */
    suspend fun disconnectDevice(accessToken: String, deviceId: String)

    /**
     * Gives Imagina the Firebase token of this phone (`PUT /{id}/push`), so it can wake the app when new mail
     * arrives. Throws [ImaginaDeviceRevokedException] when the device was disconnected.
     */
    suspend fun registerPushToken(accessToken: String, deviceId: String, token: String)

    /** Imagina forgets the Firebase token of this phone (`DELETE /{id}/push`). */
    suspend fun forgetPushToken(accessToken: String, deviceId: String)
}

data class ImaginaDeviceRegistration(val deviceId: String, val mailboxes: List<ImaginaMailAccount>)

data class ImaginaDeviceStatus(
    val revoked: Boolean,
    val changed: Boolean,
    val mailboxes: List<ImaginaMailboxStatus>,
)

data class ImaginaMailboxStatus(val address: String, val company: String, val state: String)

data class ImaginaDeviceRefresh(val added: List<ImaginaMailAccount>, val removed: List<String>)

/** A mailbox with the credential Imagina made for this device. */
data class ImaginaMailAccount(
    val address: String,
    val name: String,
    val company: String,
    val username: String,
    val password: String,
    val imap: ImaginaServer,
    val smtp: ImaginaServer,
) {
    /** Never prints the password. */
    override fun toString(): String = "ImaginaMailAccount(address=$address, company=$company)"
}

data class ImaginaServer(val host: String, val port: Int, val security: String)

class HttpImaginaDevicesApi(
    private val baseUrl: String = ImaginaAuth.DEVICES_API_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ImaginaDevicesApi {

    override suspend fun registerDevice(accessToken: String, name: String): ImaginaDeviceRegistration {
        val body = buildJsonObject {
            put(key = "name", value = name.take(MAX_DEVICE_NAME_LENGTH))
            put(key = "platform", value = "ANDROID")
        }
        val response = request(
            method = "POST",
            url = baseUrl,
            accessToken = accessToken,
            body = body.toString(),
        ).orFail()

        return parse(response) {
            ImaginaDeviceRegistration(
                deviceId = getValue("device").jsonObject.string("id"),
                mailboxes = array("mailboxes").map { it.jsonObject.toMailAccount() },
            )
        }
    }

    override suspend fun getDevice(accessToken: String, deviceId: String): ImaginaDeviceStatus {
        val response = request(method = "GET", url = "$baseUrl/$deviceId", accessToken = accessToken).orFail()

        return parse(response) {
            ImaginaDeviceStatus(
                revoked = boolean("revoked"),
                changed = boolean("changed"),
                mailboxes = array("mailboxes", required = false).map { element ->
                    val mailbox = element.jsonObject
                    ImaginaMailboxStatus(
                        address = mailbox.string("address"),
                        company = mailbox.optionalString("company"),
                        state = mailbox.optionalString("state"),
                    )
                },
            )
        }
    }

    override suspend fun refreshDevice(accessToken: String, deviceId: String): ImaginaDeviceRefresh {
        val response = request(
            method = "POST",
            url = "$baseUrl/$deviceId/refresh",
            accessToken = accessToken,
            body = "{}",
        )
        if (response.status == HttpURLConnection.HTTP_GONE) throw ImaginaDeviceRevokedException()

        return parse(response.orFail()) {
            ImaginaDeviceRefresh(
                added = array("added", required = false).map { it.jsonObject.toMailAccount() },
                removed = array("removed", required = false).map { it.jsonPrimitive.content },
            )
        }
    }

    override suspend fun disconnectDevice(accessToken: String, deviceId: String) {
        request(method = "DELETE", url = "$baseUrl/$deviceId", accessToken = accessToken).orFail()
    }

    override suspend fun registerPushToken(accessToken: String, deviceId: String, token: String) {
        val body = buildJsonObject { put(key = "token", value = token) }
        request(
            method = "PUT",
            url = "$baseUrl/$deviceId/push",
            accessToken = accessToken,
            body = body.toString(),
        ).orFailOrRevoked()
    }

    override suspend fun forgetPushToken(accessToken: String, deviceId: String) {
        request(method = "DELETE", url = "$baseUrl/$deviceId/push", accessToken = accessToken).orFailOrRevoked()
    }

    private fun HttpResult.orFailOrRevoked(): HttpResult {
        if (status == HttpURLConnection.HTTP_GONE) throw ImaginaDeviceRevokedException()

        return orFail()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> parse(response: HttpResult, block: JsonObject.() -> T): T {
        return try {
            Json.parseToJsonElement(response.body).jsonObject.block()
        } catch (error: Exception) {
            // Not the JSON the contract describes: report it as a refusal, never as a crash
            throw ImaginaApiException(
                status = response.status,
                code = "invalid_response",
                serverMessage = null,
                cause = error,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun HttpResult.orFail(): HttpResult {
        if (status in HTTP_SUCCESS) return this

        val json = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            null
        }
        throw ImaginaApiException(
            status = status,
            code = json?.optionalString("error")?.takeIf { it.isNotBlank() },
            serverMessage = json?.optionalString("message")?.takeIf { it.isNotBlank() },
        )
    }

    private suspend fun request(
        method: String,
        url: String,
        accessToken: String,
        body: String? = null,
    ): HttpResult = withContext(ioDispatcher) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

            val status = connection.responseCode
            val stream = if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            HttpResult(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (e: IOException) {
            throw ImaginaNetworkException(e)
        } finally {
            connection.disconnect()
        }
    }

    private class HttpResult(val status: Int, val body: String)

    private companion object {
        const val MAX_DEVICE_NAME_LENGTH = 80
        const val TIMEOUT_MILLIS = 20_000
        val HTTP_SUCCESS = 200..299
    }
}

private fun JsonObject.toMailAccount() = ImaginaMailAccount(
    address = string("address"),
    name = optionalString("name"),
    company = optionalString("company"),
    username = string("username"),
    password = string("password"),
    imap = getValue("imap").jsonObject.toServer(),
    smtp = getValue("smtp").jsonObject.toServer(),
)

private fun JsonObject.toServer() = ImaginaServer(
    host = string("host"),
    port = getValue("port").jsonPrimitive.int,
    security = string("security"),
)

private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

private fun JsonObject.optionalString(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.boolean(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false

private fun JsonObject.array(name: String, required: Boolean = true): JsonArray {
    val element = this[name]
    return when {
        element != null -> element.jsonArray
        required -> throw NoSuchElementException("Key $name is missing")
        else -> JsonArray(emptyList())
    }
}

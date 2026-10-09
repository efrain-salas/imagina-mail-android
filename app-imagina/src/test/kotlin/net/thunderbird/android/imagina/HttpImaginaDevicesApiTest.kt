package net.thunderbird.android.imagina

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** Talks to a local server that answers like Imagina's devices API (docs/PLAN_CLOUD.md, C7.1). */
class HttpImaginaDevicesApiTest {
    private class Request(val method: String, val path: String, val authorization: String?, val body: String)

    private val requests = mutableListOf<Request>()
    private var status = 200
    private var response = "{}"
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> answer(exchange) }
        start()
    }
    private val api = HttpImaginaDevicesApi(baseUrl = "http://127.0.0.1:${server.address.port}/api/v1/cloud/devices")

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    private fun answer(exchange: HttpExchange) {
        requests += Request(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            body = exchange.requestBody.bufferedReader().readText(),
        )
        val bytes = response.toByteArray()
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        exchange.close()
    }

    @Test
    fun `registerDevice sends the name and platform and reads the mailboxes`() = runTest {
        status = 201
        response = """
            {
              "device": {"id": "0199-aaaa", "name": "Google Pixel 8", "platform": "ANDROID",
                         "method": "IMAGINA_MAIL", "revoked": false},
              "cloud": null,
              "mailboxes": [
                {"address": "ana@casapepe.com", "name": "Ana Pérez", "company": "Casa Pepe",
                 "username": "ana.android@casapepe.com", "password": "p4ss",
                 "imap": {"host": "imap.migadu.com", "port": 993, "security": "ssl"},
                 "smtp": {"host": "smtp.migadu.com", "port": 587, "security": "starttls"}}
              ]
            }
        """.trimIndent()

        val registration = api.registerDevice(accessToken = "token-1", name = "Google Pixel 8")

        assertThat(registration.deviceId).isEqualTo("0199-aaaa")
        assertThat(registration.mailboxes.single()).isEqualTo(
            ImaginaMailAccount(
                address = "ana@casapepe.com",
                name = "Ana Pérez",
                company = "Casa Pepe",
                username = "ana.android@casapepe.com",
                password = "p4ss",
                imap = ImaginaServer(host = "imap.migadu.com", port = 993, security = "ssl"),
                smtp = ImaginaServer(host = "smtp.migadu.com", port = 587, security = "starttls"),
            ),
        )
        val request = requests.single()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/v1/cloud/devices")
        assertThat(request.authorization).isEqualTo("Bearer token-1")
        assertThat(request.body).isEqualTo("""{"name":"Google Pixel 8","platform":"ANDROID"}""")
    }

    @Test
    fun `registerDevice keeps the name within 80 characters`() = runTest {
        status = 201
        response = """{"device": {"id": "d"}, "mailboxes": []}"""

        api.registerDevice(accessToken = "token", name = "x".repeat(120))

        assertThat(requests.single().body).isEqualTo("""{"name":"${"x".repeat(80)}","platform":"ANDROID"}""")
    }

    @Test
    fun `a refusal carries the code and the sentence of Imagina`() = runTest {
        status = 403
        response = """{"error": "no_seat", "message": "Todavía no tienes buzón."}"""

        assertFailure { api.registerDevice(accessToken = "token", name = "Pixel") }
            .isInstanceOf<ImaginaApiException>()
            .all {
                prop(ImaginaApiException::status).isEqualTo(403)
                prop(ImaginaApiException::code).isEqualTo("no_seat")
                prop(ImaginaApiException::serverMessage).isEqualTo("Todavía no tienes buzón.")
            }
    }

    @Test
    fun `a refusal that is not JSON still fails with its status`() = runTest {
        status = 503
        response = "<html>Service Unavailable</html>"

        assertFailure { api.getDevice(accessToken = "token", deviceId = "d") }
            .isInstanceOf<ImaginaApiException>()
            .prop(ImaginaApiException::status).isEqualTo(503)
    }

    @Test
    fun `getDevice reads the flags and the mailboxes`() = runTest {
        response = """
            {"device": {"id": "d", "revoked": false}, "revoked": false, "changed": true,
             "mailboxes": [{"address": "ana@casapepe.com", "company": "Casa Pepe", "state": "added"},
                           {"address": "old@casapepe.com", "company": "Casa Pepe", "state": "removed"}],
             "mailProfile": null}
        """.trimIndent()

        val device = api.getDevice(accessToken = "token", deviceId = "d")

        assertThat(device.revoked).isFalse()
        assertThat(device.changed).isTrue()
        assertThat(device.mailboxes.map { it.address to it.state })
            .containsExactly("ana@casapepe.com" to "added", "old@casapepe.com" to "removed")
        assertThat(requests.single().method).isEqualTo("GET")
        assertThat(requests.single().path).isEqualTo("/api/v1/cloud/devices/d")
    }

    @Test
    fun `refreshDevice reads the added mailboxes and the removed addresses`() = runTest {
        response = """
            {"added": [{"address": "info@casapepe.com", "name": "Info", "company": "Casa Pepe",
                        "username": "info.android@casapepe.com", "password": "p",
                        "imap": {"host": "imap.migadu.com", "port": 993, "security": "ssl"},
                        "smtp": {"host": "smtp.migadu.com", "port": 465, "security": "ssl"}}],
             "removed": ["old@casapepe.com"]}
        """.trimIndent()

        val refresh = api.refreshDevice(accessToken = "token", deviceId = "d")

        assertThat(refresh.added.map { it.address }).containsExactly("info@casapepe.com")
        assertThat(refresh.removed).containsExactly("old@casapepe.com")
        assertThat(requests.single().method).isEqualTo("POST")
        assertThat(requests.single().path).isEqualTo("/api/v1/cloud/devices/d/refresh")
    }

    @Test
    fun `refreshDevice tells when the device was disconnected`() = runTest {
        status = 410
        response = """{"error": "revoked", "message": "Este móvil se desconectó."}"""

        assertFailure {
            api.refreshDevice(accessToken = "token", deviceId = "d")
        }.isInstanceOf<ImaginaDeviceRevokedException>()
    }

    @Test
    fun `disconnectDevice accepts the empty answer`() = runTest {
        status = 204

        api.disconnectDevice(accessToken = "token", deviceId = "d")

        assertThat(requests.single().method).isEqualTo("DELETE")
        assertThat(requests.single().path).isEqualTo("/api/v1/cloud/devices/d")
        assertThat(requests.single().body).isEmpty()
    }

    @Test
    fun `registerPushToken puts the token and accepts the empty answer`() = runTest {
        status = 204

        api.registerPushToken(accessToken = "token-1", deviceId = "d", token = "fcm:abc")

        val request = requests.single()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/v1/cloud/devices/d/push")
        assertThat(request.authorization).isEqualTo("Bearer token-1")
        assertThat(request.body).isEqualTo("""{"token":"fcm:abc"}""")
    }

    @Test
    fun `registerPushToken tells when the device was disconnected`() = runTest {
        status = 410
        response = """{"error": "revoked", "message": "Este móvil se desconectó."}"""

        assertFailure { api.registerPushToken(accessToken = "token", deviceId = "d", token = "t") }
            .isInstanceOf<ImaginaDeviceRevokedException>()
    }

    @Test
    fun `registerPushToken passes on the refusal of Imagina`() = runTest {
        status = 422
        response = """{"error": "invalid_token", "message": "El token no vale."}"""

        assertFailure { api.registerPushToken(accessToken = "token", deviceId = "d", token = "t") }
            .isInstanceOf<ImaginaApiException>()
            .all {
                prop(ImaginaApiException::status).isEqualTo(422)
                prop(ImaginaApiException::code).isEqualTo("invalid_token")
            }
    }

    @Test
    fun `forgetPushToken deletes the push of the device and accepts the empty answer`() = runTest {
        status = 204

        api.forgetPushToken(accessToken = "token-1", deviceId = "d")

        val request = requests.single()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/v1/cloud/devices/d/push")
        assertThat(request.authorization).isEqualTo("Bearer token-1")
        assertThat(request.body).isEmpty()
    }

    @Test
    fun `forgetPushToken tells when the device was disconnected`() = runTest {
        status = 410
        response = """{"error": "revoked"}"""

        assertFailure { api.forgetPushToken(accessToken = "token", deviceId = "d") }
            .isInstanceOf<ImaginaDeviceRevokedException>()
    }

    @Test
    fun `an answer that is not what the contract describes fails as a refusal`() = runTest {
        status = 201
        response = """{"device": {"id": "d"}}"""

        assertFailure { api.registerDevice(accessToken = "token", name = "Pixel") }
            .isInstanceOf<ImaginaApiException>()
            .prop(ImaginaApiException::code).isEqualTo("invalid_response")
    }

    @Test
    fun `a server that cannot be reached fails as a network error`() = runTest {
        server.stop(0)

        assertFailure { api.getDevice(accessToken = "token", deviceId = "d") }.isInstanceOf<ImaginaNetworkException>()
    }
}

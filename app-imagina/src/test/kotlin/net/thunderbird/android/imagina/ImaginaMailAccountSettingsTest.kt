package net.thunderbird.android.imagina

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import kotlin.test.Test

class ImaginaMailAccountSettingsTest {
    @Test
    fun `the incoming server is IMAP with the identity login and the device password`() {
        val settings = mailbox("ana@casapepe.com").incomingServerSettings()

        assertThat(settings.type).isEqualTo("imap")
        assertThat(settings.host).isEqualTo("imap.migadu.com")
        assertThat(settings.port).isEqualTo(993)
        assertThat(settings.connectionSecurity).isEqualTo(ConnectionSecurity.SSL_TLS_REQUIRED)
        assertThat(settings.authenticationType).isEqualTo(AuthType.PLAIN)
        assertThat(settings.username).isEqualTo("login+ana@casapepe.com")
        assertThat(settings.password).isEqualTo("secret-ana@casapepe.com")
    }

    @Test
    fun `the outgoing server maps starttls`() {
        val mailbox = mailbox(
            "ana@casapepe.com",
        ).copy(smtp = ImaginaServer(host = "smtp.migadu.com", port = 587, security = "starttls"))

        val settings = mailbox.outgoingServerSettings()

        assertThat(settings.type).isEqualTo("smtp")
        assertThat(settings.port).isEqualTo(587)
        assertThat(settings.connectionSecurity).isEqualTo(ConnectionSecurity.STARTTLS_REQUIRED)
    }

    @Test
    fun `an unknown connection security is refused instead of guessed`() {
        val mailbox = mailbox(
            "ana@casapepe.com",
        ).copy(imap = ImaginaServer(host = "imap.migadu.com", port = 143, security = "none"))

        assertFailure { mailbox.incomingServerSettings() }.isInstanceOf<ImaginaAccountException>()
    }

    @Test
    fun `the managed accounts survive being stored`() {
        val accounts = listOf(
            ImaginaManagedAccount(address = "ana@casapepe.com", uuid = "uuid-1", company = "Casa Pepe"),
            ImaginaManagedAccount(address = "ana@cafe.es", uuid = "uuid-2", company = ""),
        )

        assertThat(decodeAccounts(encodeAccounts(accounts))).containsExactly(*accounts.toTypedArray())
    }

    @Test
    fun `stored accounts that cannot be read are dropped`() {
        assertThat(decodeAccounts("not json")).isEmpty()
        assertThat(decodeAccounts(null)).isEmpty()
    }
}

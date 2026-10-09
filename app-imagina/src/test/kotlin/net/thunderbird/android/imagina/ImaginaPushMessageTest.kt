package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.matches
import kotlin.test.Test

class ImaginaPushMessageTest {

    @Test
    fun `the mailbox is the first 16 hex characters of the sha256 of the device and the address`() {
        // printf 'device-1:ana@casapepe.com' | shasum -a 256 -> d4a05f69cbf92ab4a115ad26...
        assertThat(ImaginaPushMessage.mailboxHash("device-1", "ana@casapepe.com")).isEqualTo("d4a05f69cbf92ab4")
        // printf '0199-aaaa:info@casapepe.com' | shasum -a 256 -> b5bcdaddf4cdc46f603f796f...
        assertThat(ImaginaPushMessage.mailboxHash("0199-aaaa", "info@casapepe.com")).isEqualTo("b5bcdaddf4cdc46f")
    }

    @Test
    fun `the mailbox is 16 lowercase hex characters`() {
        val hash = ImaginaPushMessage.mailboxHash("device-1", "ana@casapepe.com")

        assertThat(hash).hasLength(16)
        assertThat(hash).matches(Regex("[0-9a-f]{16}"))
    }

    @Test
    fun `the address counts in lowercase`() {
        assertThat(ImaginaPushMessage.mailboxHash("device-1", "Ana@CasaPepe.COM"))
            .isEqualTo(ImaginaPushMessage.mailboxHash("device-1", "ana@casapepe.com"))
    }

    @Test
    fun `the same address has another mailbox on another device`() {
        assertThat(ImaginaPushMessage.mailboxHash("device-2", "ana@casapepe.com"))
            .isNotEqualTo(ImaginaPushMessage.mailboxHash("device-1", "ana@casapepe.com"))
    }

    @Test
    fun `a new mail push gives its mailbox`() {
        val data = mapOf("type" to "new_mail", "mailbox" to "d4a05f69cbf92ab4")

        assertThat(ImaginaPushMessage.newMailMailbox(data)).isEqualTo("d4a05f69cbf92ab4")
    }

    @Test
    fun `the mailbox of a push is read in lowercase`() {
        val data = mapOf("type" to "new_mail", "mailbox" to "D4A05F69CBF92AB4")

        assertThat(ImaginaPushMessage.newMailMailbox(data)).isEqualTo("d4a05f69cbf92ab4")
    }

    @Test
    fun `anything else is not a new mail push`() {
        val mailbox = "d4a05f69cbf92ab4"

        assertThat(ImaginaPushMessage.newMailMailbox(mapOf("type" to "other", "mailbox" to mailbox))).isNull()
        assertThat(ImaginaPushMessage.newMailMailbox(mapOf("mailbox" to mailbox))).isNull()
        assertThat(ImaginaPushMessage.newMailMailbox(mapOf("type" to "new_mail"))).isNull()
        assertThat(ImaginaPushMessage.newMailMailbox(mapOf("type" to "new_mail", "mailbox" to ""))).isNull()
        assertThat(ImaginaPushMessage.newMailMailbox(emptyMap())).isNull()
    }
}

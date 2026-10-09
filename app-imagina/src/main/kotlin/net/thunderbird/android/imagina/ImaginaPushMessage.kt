package net.thunderbird.android.imagina

import java.security.MessageDigest

/**
 * What Imagina sends through Firebase Cloud Messaging when mail arrives: a high-priority data message
 * `{"type": "new_mail", "mailbox": "<16 hex>"}` with no content. The mailbox is not its address but a short
 * hash that only this device can compute, so Firebase never sees who the mail is for.
 */
object ImaginaPushMessage {
    const val TYPE_NEW_MAIL = "new_mail"

    private const val KEY_TYPE = "type"
    private const val KEY_MAILBOX = "mailbox"
    private const val HASH_BYTES = 8

    /**
     * The first 16 lowercase hex characters of `sha256("{deviceId}:{lowercased mailbox address}")`: how Imagina
     * names a mailbox to a device in a push.
     */
    fun mailboxHash(deviceId: String, address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$deviceId:${address.lowercase()}".toByteArray(Charsets.UTF_8))

        return digest.take(HASH_BYTES).joinToString(separator = "") { "%02x".format(it) }
    }

    /** The mailbox hash of a `new_mail` push, or `null` when [data] is not one. */
    fun newMailMailbox(data: Map<String, String>): String? {
        if (data[KEY_TYPE] != TYPE_NEW_MAIL) return null

        return data[KEY_MAILBOX]?.lowercase()?.takeIf { it.isNotEmpty() }
    }
}

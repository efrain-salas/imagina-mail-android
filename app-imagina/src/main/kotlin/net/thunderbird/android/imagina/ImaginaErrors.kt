package net.thunderbird.android.imagina

/**
 * What can go wrong while talking to Imagina or while setting the accounts up. The sign-in screen
 * and the background sync decide what to do (or say) from the type.
 */
sealed class ImaginaException(message: String?, cause: Throwable? = null) : Exception(message, cause)

/**
 * Imagina refused a request. [code] is the `error` of its answer (`no_seat`, `not_ready`…) and
 * [serverMessage] the sentence it wrote, already in the person's language.
 */
class ImaginaApiException(
    val status: Int,
    val code: String?,
    val serverMessage: String?,
    cause: Throwable? = null,
) : ImaginaException("Imagina answered $status ${code.orEmpty()} ${serverMessage.orEmpty()}".trim(), cause)

/** Imagina could not be reached; trying again later may work. */
class ImaginaNetworkException(cause: Throwable) : ImaginaException(cause.message, cause)

/** The sign-in no longer works (token refused or gone): the person has to «Entrar con Imagina» again. */
class ImaginaSignInRequiredException(cause: Throwable? = null) :
    ImaginaException("Sign-in with Imagina required", cause)

/** Imagina disconnected this device (HTTP 410 on refresh). */
class ImaginaDeviceRevokedException : ImaginaException("Imagina disconnected this device")

/** Imagina had no mailbox to give. */
class ImaginaNoMailboxesException : ImaginaException("Imagina returned no mailboxes")

/** A mailbox account could not be created or updated on this phone. */
class ImaginaAccountException(message: String?, cause: Throwable? = null) : ImaginaException(message, cause)

package net.thunderbird.android.imagina

/** Why the person sees «Entrar con Imagina». */
enum class ImaginaSignInMode {
    /** First screen of the app: no account yet. */
    Onboarding,

    /** The app has accounts but they need Imagina again: a credential stopped working, the sync lost its sign-in… */
    Reconnect,
}

package net.thunderbird.android.imagina

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import app.k9mail.feature.account.edit.navigation.AccountEditNavigation
import app.k9mail.feature.account.edit.navigation.AccountEditRoute
import app.k9mail.feature.account.setup.navigation.AccountSetupNavigation
import app.k9mail.feature.account.setup.navigation.AccountSetupRoute
import app.k9mail.feature.onboarding.main.navigation.OnboardingNavigation
import app.k9mail.feature.onboarding.main.navigation.OnboardingRoute
import app.k9mail.feature.onboarding.permissions.ui.PermissionsScreen
import net.thunderbird.core.ui.navigation.deepLinkComposable
import net.thunderbird.feature.thundermail.navigation.ThundermailNavigation
import net.thunderbird.feature.thundermail.navigation.ThundermailRoute
import net.thunderbird.feature.thundermail.navigation.ThundermailRoute.Companion.ACCOUNT_ID_ROUTE_PARAM

/*
 * Imagina Mail never asks for a server, an email address or a password: every way into the app
 * ends in «Entrar con Imagina». The launcher (feature/launcher) gets its screens from navigation
 * objects in the dependency injection; the ones below take the place of Thunderbird's (Koin
 * definitions can be overridden in Imagina Mail, see ThunderbirdApp.allowsDefinitionOverride).
 */

/**
 * The first screen of the app (Thunderbird has a welcome with its and Mozilla's logos and texts):
 * «Entrar con Imagina», then the permissions step, the alarms that keep new mail arriving at once, then the inbox.
 */
class ImaginaOnboardingNavigation : OnboardingNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (OnboardingRoute) -> Unit,
    ) {
        navGraphBuilder.deepLinkComposable<OnboardingRoute.Onboarding>(
            basePath = OnboardingRoute.Onboarding.BASE_PATH,
        ) {
            ImaginaOnboarding(onFinish = { accountUuid -> onFinish(OnboardingRoute.Onboarding(accountUuid)) })
        }
    }
}

@Composable
private fun ImaginaOnboarding(onFinish: (accountUuid: String) -> Unit) {
    var accountUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionsDone by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }

    val signedInAccountUuid = accountUuid
    when {
        signedInAccountUuid == null -> {
            ImaginaSignInScreen(mode = ImaginaSignInMode.Onboarding, onSignedIn = { accountUuid = it })
        }

        !permissionsDone -> PermissionsScreen(onNext = { permissionsDone = true })

        else -> ImaginaInstantMailScreen(
            onNext = {
                if (!finished) {
                    finished = true
                    onFinish(signedInAccountUuid)
                }
            },
        )
    }
}

/**
 * Where Thunderbird sends the person to fix the servers or the password of an account: from the
 * «authentication failed» notification, the account settings and the message list. A mailbox
 * credential is Imagina's to give, so the person signs in with Imagina again instead.
 */
class ImaginaAccountEditNavigation : AccountEditNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (AccountEditRoute) -> Unit,
    ) {
        with(navGraphBuilder) {
            deepLinkComposable<AccountEditRoute.IncomingServerSettings>(
                basePath = AccountEditRoute.IncomingServerSettings.BASE_PATH,
            ) {
                ImaginaSignInScreen(mode = ImaginaSignInMode.Reconnect, onSignedIn = { onBack() }, onDismiss = onBack)
            }

            deepLinkComposable<AccountEditRoute.OutgoingServerSettings>(
                basePath = AccountEditRoute.OutgoingServerSettings.BASE_PATH,
            ) {
                ImaginaSignInScreen(mode = ImaginaSignInMode.Reconnect, onSignedIn = { onBack() }, onDismiss = onBack)
            }
        }
    }
}

/**
 * «Añadir cuenta» (and the app opening with no account to write with): the mailboxes come from
 * Imagina, so signing in again brings the ones that are missing.
 */
class ImaginaAccountSetupNavigation : AccountSetupNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (AccountSetupRoute) -> Unit,
    ) {
        navGraphBuilder.deepLinkComposable<AccountSetupRoute.AccountSetup>(
            basePath = AccountSetupRoute.AccountSetup.BASE_PATH,
        ) {
            ImaginaSignInScreen(mode = ImaginaSignInMode.Reconnect, onSignedIn = { onBack() }, onDismiss = onBack)
        }
    }
}

/**
 * Thundermail's routes (add account, sign in, QR code, manual setup…) all end in «Entrar con
 * Imagina» too, in case something still navigates there.
 */
class ImaginaNavigation : ThundermailNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (ThundermailRoute) -> Unit,
    ) {
        with(navGraphBuilder) {
            deepLinkComposable<ThundermailRoute.AddAccount>(basePath = ThundermailRoute.THUNDERMAIL_ADD_ACCOUNT_ROUTE) {
                ImaginaSignInScreen(
                    mode = ImaginaSignInMode.Onboarding,
                    onSignedIn = { accountUuid -> onFinish(ThundermailRoute.Permissions(accountUuid)) },
                )
            }

            deepLinkComposable<ThundermailRoute.SignInWithThundermail>(
                basePath = ThundermailRoute.SIGN_IN_WITH_THUNDERMAIL_ROUTE,
            ) {
                ImaginaSignInScreen(
                    mode = ImaginaSignInMode.Onboarding,
                    onSignedIn = { accountUuid -> onFinish(ThundermailRoute.Permissions(accountUuid)) },
                )
            }

            deepLinkComposable<ThundermailRoute.ScanQrCode>(basePath = ThundermailRoute.SCAN_QR_CODE_ROUTE) {
                ImaginaSignInScreen(mode = ImaginaSignInMode.Reconnect, onSignedIn = { onBack() }, onDismiss = onBack)
            }

            deepLinkComposable<ThundermailRoute.IncomingSettings>(basePath = ThundermailRoute.INCOMING_SETTINGS_ROUTE) {
                ImaginaSignInScreen(mode = ImaginaSignInMode.Reconnect, onSignedIn = { onBack() }, onDismiss = onBack)
            }

            deepLinkComposable<ThundermailRoute.AccountSetup>(basePath = ThundermailRoute.ACCOUNT_SETUP_ROUTE) {
                ImaginaSignInScreen(mode = ImaginaSignInMode.Reconnect, onSignedIn = { onBack() }, onDismiss = onBack)
            }

            deepLinkComposable<ThundermailRoute.Permissions>(basePath = ThundermailRoute.PERMISSIONS_ROUTE) {
                val accountId = requireNotNull(it.arguments?.getString(ACCOUNT_ID_ROUTE_PARAM))
                PermissionsScreen(onNext = { onFinish(ThundermailRoute.OnboardComplete(accountId)) })
            }
        }
    }
}

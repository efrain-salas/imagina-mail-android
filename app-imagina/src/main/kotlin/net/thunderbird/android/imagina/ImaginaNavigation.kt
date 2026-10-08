package net.thunderbird.android.imagina

import androidx.navigation.NavGraphBuilder
import app.k9mail.feature.account.setup.navigation.AccountSetupNavHost
import app.k9mail.feature.account.setup.navigation.AccountSetupRoute
import app.k9mail.feature.onboarding.permissions.ui.PermissionsScreen
import app.k9mail.feature.settings.import.ui.SettingsImportAction
import app.k9mail.feature.settings.import.ui.SettingsImportScreen
import net.thunderbird.core.ui.navigation.deepLinkComposable
import net.thunderbird.feature.thundermail.navigation.ThundermailNavigation
import net.thunderbird.feature.thundermail.navigation.ThundermailRoute
import net.thunderbird.feature.thundermail.navigation.ThundermailRoute.Companion.ACCOUNT_ID_ROUTE_PARAM

/**
 * Where Thunderbird's onboarding goes after «Empezar»: in Imagina Mail, the «Entrar con Imagina»
 * screen instead of Thundermail's. It plugs into the same navigation hook Thunderbird uses for
 * Thundermail, so nothing in the shared feature modules changes.
 */
class ImaginaNavigation : ThundermailNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (ThundermailRoute) -> Unit,
    ) {
        with(navGraphBuilder) {
            deepLinkComposable<ThundermailRoute.AddAccount>(basePath = ThundermailRoute.THUNDERMAIL_ADD_ACCOUNT_ROUTE) {
                ImaginaSignInScreen(onAccountCreated = { onFinish(ThundermailRoute.Permissions(it)) })
            }

            deepLinkComposable<ThundermailRoute.SignInWithThundermail>(
                basePath = ThundermailRoute.SIGN_IN_WITH_THUNDERMAIL_ROUTE,
            ) {
                ImaginaSignInScreen(onAccountCreated = { onFinish(ThundermailRoute.Permissions(it)) })
            }

            deepLinkComposable<ThundermailRoute.Permissions>(basePath = ThundermailRoute.PERMISSIONS_ROUTE) {
                val accountId = requireNotNull(it.arguments?.getString(ACCOUNT_ID_ROUTE_PARAM))
                PermissionsScreen(onNext = { onFinish(ThundermailRoute.OnboardComplete(accountId)) })
            }

            deepLinkComposable<ThundermailRoute.ScanQrCode>(basePath = ThundermailRoute.SCAN_QR_CODE_ROUTE) {
                SettingsImportScreen(
                    action = SettingsImportAction.ScanQrCode,
                    onImportSuccess = { onFinish(ThundermailRoute.AccountSetup(accountId = null)) },
                    onBack = onBack,
                )
            }

            deepLinkComposable<ThundermailRoute.AccountSetup>(basePath = ThundermailRoute.ACCOUNT_SETUP_ROUTE) {
                AccountSetupNavHost(
                    onBack = onBack,
                    onFinish = { route: AccountSetupRoute ->
                        when (route) {
                            is AccountSetupRoute.AccountSetup ->
                                onFinish(ThundermailRoute.Permissions(requireNotNull(route.accountId)))

                            AccountSetupRoute.ThundermailScanQrCode -> onFinish(ThundermailRoute.ScanQrCode)

                            AccountSetupRoute.ThundermailSignIn -> onFinish(ThundermailRoute.SignInWithThundermail)
                        }
                    },
                    skipToIncomingValidation = false,
                )
            }
        }
    }
}

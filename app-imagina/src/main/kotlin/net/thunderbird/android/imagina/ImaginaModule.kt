package net.thunderbird.android.imagina

import app.k9mail.feature.account.edit.navigation.AccountEditNavigation
import app.k9mail.feature.account.setup.navigation.AccountSetupNavigation
import app.k9mail.feature.onboarding.main.navigation.OnboardingNavigation
import net.thunderbird.feature.thundermail.navigation.ThundermailNavigation
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Everything Imagina Mail adds to Thunderbird's dependency injection. It is loaded last, so what it
 * declares for a type Thunderbird already has (the navigation objects) replaces it.
 */
val imaginaModule = module {
    // Every way into the app ends in «Entrar con Imagina», never in a password field
    single<OnboardingNavigation> { ImaginaOnboardingNavigation() }
    single<AccountEditNavigation> { ImaginaAccountEditNavigation() }
    single<AccountSetupNavigation> { ImaginaAccountSetupNavigation() }
    single<ThundermailNavigation> { ImaginaNavigation() }

    // Link with Imagina's devices API
    single<ImaginaDeviceStore> { SharedPreferencesImaginaDeviceStore(context = androidContext()) }
    single<ImaginaDevicesApi> { HttpImaginaDevicesApi() }
    single<ImaginaSession> { AppAuthImaginaSession(context = androidContext(), store = get()) }
    single<ImaginaSyncScheduler> { WorkManagerImaginaSyncScheduler(context = androidContext()) }
    single<ImaginaLocalAccounts> {
        ImaginaAccountProvisioner(
            accountCreator = get(),
            serverSettingsUpdater = get(),
            accountRemover = get(),
            preferences = get(),
        )
    }
    single {
        ImaginaDeviceSynchronizer(
            api = get(),
            session = get(),
            store = get(),
            localAccounts = get(),
            scheduler = get(),
            logger = get(),
        )
    }
    single {
        ImaginaStartup(
            application = androidApplication(),
            store = get(),
            synchronizer = get(),
            scheduler = get(),
            preferences = get(),
        )
    }

    viewModel { ImaginaSignInViewModel(oAuth = get(), synchronizer = get(), logger = get()) }
}

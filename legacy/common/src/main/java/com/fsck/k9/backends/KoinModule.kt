package com.fsck.k9.backends

import com.fsck.k9.backend.BackendManager
import com.fsck.k9.backend.imap.BackendIdleRefreshManager
import com.fsck.k9.backend.imap.SystemAlarmManager
import com.fsck.k9.mail.oauth.OAuth2TokenProviderFactory
import com.fsck.k9.mail.store.imap.IdleRefreshManager
import com.fsck.k9.mail.transport.smtp.SMTP_HELLO_NAME
import net.thunderbird.backend.api.BackendFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

val backendsModule = module {
    single {
        val developmentBackends = get<Map<String, BackendFactory>>(named("developmentBackends"))
        BackendManager(
            backendFactories = mapOf(
                "imap" to get<ImapBackendFactory>(),
                "pop3" to get<Pop3BackendFactory>(),
            ) + developmentBackends,
            accountManager = get(),
        )
    }
    single<ImapBackendFactory> {
        DefaultImapBackendFactory(
            accountManager = get(),
            powerManager = get(),
            idleRefreshManager = get(),
            backendStorageFactory = get(),
            trustedSocketFactory = get(),
            context = get(),
            clientInfoAppName = get(named("ClientInfoAppName")),
            clientInfoAppVersion = get(named("ClientInfoAppVersion")),
            smtpHelloName = smtpHelloName(),
        )
    }
    single<SystemAlarmManager> { AndroidAlarmManager(context = get(), alarmManager = get()) }
    single<IdleRefreshManager> { BackendIdleRefreshManager(alarmManager = get()) }
    single<Pop3BackendFactory> {
        DefaultPop3BackendFactory(
            accountManager = get(),
            backendStorageFactory = get(),
            trustedSocketFactory = get(),
            smtpHelloName = smtpHelloName(),
        )
    }
    single<OAuth2TokenProviderFactory> { RealOAuth2TokenProviderFactory(context = get()) }
}

/**
 * The name the app presents itself with to SMTP servers. Imagina Mail (IMAGINA.md) declares a string named
 * `SmtpHelloName`; Thunderbird and K-9 Mail do not and keep [SMTP_HELLO_NAME].
 */
private fun Scope.smtpHelloName(): String = getOrNull(named("SmtpHelloName")) ?: SMTP_HELLO_NAME

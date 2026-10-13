package net.thunderbird.android.imagina

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.thunderbird.android.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextHeadlineMedium
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * The first screen of Imagina Mail: a single «Entrar con Imagina» button. Also what the person sees
 * instead of a password field when the credential of a mailbox stops working.
 *
 * @param onSignedIn the sign-in worked and the accounts are set up; gets the uuid of the account to show first.
 * @param onDismiss the person gave up ([ImaginaSignInMode.Reconnect] only).
 */
@Composable
fun ImaginaSignInScreen(
    mode: ImaginaSignInMode,
    onSignedIn: (accountUuid: String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    viewModel: ImaginaSignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val oAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onOAuthResult(it.resultCode, it.data)
    }

    LaunchedEffect(viewModel) { viewModel.launchOAuth.collect { oAuthLauncher.launch(it) } }
    LaunchedEffect(viewModel) { viewModel.accountCreated.collect { onSignedIn(it) } }

    if (mode == ImaginaSignInMode.Reconnect) {
        // Without it, going back would show the screen under this one (the app's first destination)
        BackHandler(onBack = onDismiss)
    }

    ImaginaSignInContent(
        mode = mode,
        state = state,
        onSignInClick = viewModel::signIn,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@Composable
private fun ImaginaSignInContent(
    mode: ImaginaSignInMode,
    state: ImaginaSignInViewModel.UiState,
    onSignInClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val privacyUrl = stringResource(R.string.app_privacy_url)

    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = BoltTheme.spacings.quadruple),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.triple, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The brand's logotype, «imagina /mail», in place of the mark and the name as text (its reverse in the
            // dark theme comes from drawable-night). The name is what a screen reader says.
            Image(
                painter = painterResource(R.drawable.ic_imagina_wordmark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.height(WORDMARK_HEIGHT_DP.dp),
            )
            if (mode == ImaginaSignInMode.Reconnect) {
                TextHeadlineMedium(
                    text = stringResource(R.string.imagina_reconnect_title),
                    textAlign = TextAlign.Center,
                )
            }
            TextBodyLarge(
                text = when (mode) {
                    ImaginaSignInMode.Onboarding -> stringResource(R.string.imagina_sign_in_description)
                    ImaginaSignInMode.Reconnect -> stringResource(R.string.imagina_reconnect_description)
                },
                textAlign = TextAlign.Center,
            )

            if (state == ImaginaSignInViewModel.UiState.Working) {
                CircularProgressIndicator()
                TextBodyLarge(text = stringResource(R.string.imagina_sign_in_working), textAlign = TextAlign.Center)
            } else {
                if (state is ImaginaSignInViewModel.UiState.Failed) {
                    TextBodyLarge(
                        text = state.message ?: stringResource(state.reason.messageResource()),
                        textAlign = TextAlign.Center,
                    )
                }
                ButtonFilled(
                    text = stringResource(R.string.imagina_sign_in_button),
                    onClick = onSignInClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == ImaginaSignInMode.Reconnect) {
                    ButtonText(text = stringResource(R.string.imagina_reconnect_dismiss), onClick = onDismiss)
                }
                // Google Play asks for the privacy policy inside the app too, before any data is entered
                ButtonText(
                    text = stringResource(R.string.about_privacy_policy),
                    onClick = { context.openInBrowser(privacyUrl) },
                )
            }
        }
    }
}

private const val WORDMARK_HEIGHT_DP = 40

private fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (ignored: ActivityNotFoundException) {
        Toast.makeText(this, R.string.imagina_error_browser_not_available, Toast.LENGTH_SHORT).show()
    }
}

private fun ImaginaSignInViewModel.Reason.messageResource(): Int = when (this) {
    ImaginaSignInViewModel.Reason.BrowserNotAvailable -> R.string.imagina_error_browser_not_available
    ImaginaSignInViewModel.Reason.SignInFailed -> R.string.imagina_error_sign_in_failed
    ImaginaSignInViewModel.Reason.NoSeat -> R.string.imagina_error_no_seat
    ImaginaSignInViewModel.Reason.NotReady -> R.string.imagina_error_not_ready
    ImaginaSignInViewModel.Reason.NotSupported -> R.string.imagina_error_not_supported
    ImaginaSignInViewModel.Reason.Unavailable -> R.string.imagina_error_unavailable
    ImaginaSignInViewModel.Reason.Network -> R.string.imagina_error_network
    ImaginaSignInViewModel.Reason.NoMailboxes -> R.string.imagina_error_no_mailboxes
    ImaginaSignInViewModel.Reason.Unknown -> R.string.imagina_error_unknown
}

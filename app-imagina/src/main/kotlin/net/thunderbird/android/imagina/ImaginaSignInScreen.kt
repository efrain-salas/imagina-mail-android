package net.thunderbird.android.imagina

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.thunderbird.android.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextHeadlineMedium
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * The first screen of Imagina Mail: a single «Entrar con Imagina» button.
 */
@Composable
fun ImaginaSignInScreen(
    onAccountCreated: (accountId: String) -> Unit,
    viewModel: ImaginaSignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val oAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onOAuthResult(it.resultCode, it.data)
    }

    LaunchedEffect(viewModel) { viewModel.launchOAuth.collect { oAuthLauncher.launch(it) } }
    LaunchedEffect(viewModel) { viewModel.accountCreated.collect { onAccountCreated(it) } }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = BoltTheme.spacings.quadruple),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.triple, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_imagina_mark),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
            TextHeadlineMedium(text = "Imagina Mail", textAlign = TextAlign.Center)
            TextBodyLarge(
                text = "Entra con tu móvil y el código que te llegará por SMS. Tus buzones se configuran solos.",
                textAlign = TextAlign.Center,
            )

            when (val current = state) {
                ImaginaSignInViewModel.UiState.Working -> CircularProgressIndicator()

                else -> {
                    if (current is ImaginaSignInViewModel.UiState.Failed) {
                        TextBodyLarge(text = current.message, textAlign = TextAlign.Center)
                    }
                    ButtonFilled(
                        text = "Entrar con Imagina",
                        onClick = viewModel::signIn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

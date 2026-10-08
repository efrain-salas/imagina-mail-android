package net.thunderbird.android.imagina

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract
import app.k9mail.feature.account.oauth.ui.AccountOAuthViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * «Entrar con Imagina»: Thunderbird's OAuth flow against Imagina's identity server (custom tab
 * with the mobile number and the code), then the accounts of the person's mailboxes.
 */
class ImaginaSignInViewModel(
    private val oAuth: AccountOAuthViewModel,
    private val provisioner: ImaginaAccountProvisioner,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Working : UiState
        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private val oAuthIntents = Channel<Intent>(Channel.BUFFERED)
    val launchOAuth = oAuthIntents.receiveAsFlow()

    private val createdAccounts = Channel<String>(Channel.BUFFERED)
    val accountCreated = createdAccounts.receiveAsFlow()

    init {
        oAuth.initState(AccountOAuthContract.State(hostname = ImaginaAuth.HOST))

        oAuth.effect.onEach { effect ->
            when (effect) {
                is AccountOAuthContract.Effect.LaunchOAuth -> oAuthIntents.send(effect.intent)
                is AccountOAuthContract.Effect.NavigateNext -> provision(effect)
                AccountOAuthContract.Effect.NavigateBack -> _state.value = UiState.Idle
            }
        }.launchIn(viewModelScope)

        oAuth.state.onEach { oAuthState ->
            when (val error = oAuthState.error) {
                null -> Unit
                AccountOAuthContract.Error.Canceled -> _state.value = UiState.Idle
                AccountOAuthContract.Error.BrowserNotAvailable ->
                    _state.value = UiState.Failed("No hay un navegador disponible en este móvil.")
                else -> _state.value = UiState.Failed("No se ha podido entrar con Imagina ($error).")
            }
        }.launchIn(viewModelScope)
    }

    fun signIn() {
        _state.value = UiState.Working
        oAuth.event(AccountOAuthContract.Event.SignInClicked)
    }

    fun onOAuthResult(resultCode: Int, data: Intent?) {
        oAuth.event(AccountOAuthContract.Event.OnOAuthResult(resultCode, data))
    }

    private fun provision(effect: AccountOAuthContract.Effect.NavigateNext) {
        _state.value = UiState.Working
        viewModelScope.launch {
            provisioner.provision(effect.state).fold(
                onSuccess = { accountId -> createdAccounts.send(accountId) },
                onFailure = { error ->
                    _state.value = UiState.Failed("No se ha podido configurar tu correo: ${error.message}")
                },
            )
        }
    }
}

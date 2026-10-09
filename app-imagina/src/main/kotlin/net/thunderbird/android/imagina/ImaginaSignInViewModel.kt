package net.thunderbird.android.imagina

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.thunderbird.core.logging.Logger

/**
 * «Entrar con Imagina»: Thunderbird's OAuth flow against Imagina's identity server (custom tab
 * with the mobile number and the code), then the accounts of the person's mailboxes through
 * Imagina's devices API.
 */
class ImaginaSignInViewModel(
    private val oAuth: AccountOAuthContract.ViewModel,
    private val synchronizer: ImaginaDeviceSynchronizer,
    private val logger: Logger,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Working : UiState

        /** [message] is Imagina's own sentence, in the person's language, when it gave one. */
        data class Failed(val reason: Reason, val message: String? = null) : UiState
    }

    enum class Reason {
        BrowserNotAvailable,
        SignInFailed,
        NoSeat,
        NotReady,
        NotSupported,
        Unavailable,
        Network,
        NoMailboxes,
        Unknown,
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private val oAuthIntents = Channel<Intent>(Channel.BUFFERED)
    val launchOAuth = oAuthIntents.receiveAsFlow()

    private val signedInAccounts = Channel<String>(Channel.BUFFERED)

    /** The uuid of the account to show first, once the person is signed in and the accounts are set up. */
    val accountCreated = signedInAccounts.receiveAsFlow()

    init {
        oAuth.initState(AccountOAuthContract.State(hostname = ImaginaAuth.HOST))

        oAuth.effect.onEach { effect ->
            when (effect) {
                is AccountOAuthContract.Effect.LaunchOAuth -> oAuthIntents.send(effect.intent)
                is AccountOAuthContract.Effect.NavigateNext -> connect(effect.state)
                AccountOAuthContract.Effect.NavigateBack -> _state.value = UiState.Idle
            }
        }.launchIn(viewModelScope)

        oAuth.state.onEach { oAuthState ->
            when (oAuthState.error) {
                null -> Unit

                AccountOAuthContract.Error.Canceled -> _state.value = UiState.Idle

                AccountOAuthContract.Error.BrowserNotAvailable ->
                    _state.value = UiState.Failed(Reason.BrowserNotAvailable)

                AccountOAuthContract.Error.NotSupported,
                is AccountOAuthContract.Error.Unknown,
                -> _state.value = UiState.Failed(Reason.SignInFailed)
            }
        }.launchIn(viewModelScope)
    }

    fun signIn() {
        _state.value = UiState.Working
        if (oAuth.state.value.error == null) {
            oAuth.event(AccountOAuthContract.Event.SignInClicked)
        } else {
            oAuth.event(AccountOAuthContract.Event.OnRetryClicked)
        }
    }

    fun onOAuthResult(resultCode: Int, data: Intent?) {
        oAuth.event(AccountOAuthContract.Event.OnOAuthResult(resultCode, data))
    }

    @Suppress("TooGenericExceptionCaught")
    private fun connect(authorizationState: AuthorizationState) {
        _state.value = UiState.Working
        viewModelScope.launch {
            try {
                signedInAccounts.send(synchronizer.connect(authorizationState))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.warn(TAG, error) { "Could not set the accounts up" }
                _state.value = error.toFailure()
            }
        }
    }

    private fun Exception.toFailure(): UiState.Failed = when (this) {
        is ImaginaApiException -> UiState.Failed(reason(), serverMessage)
        is ImaginaNetworkException -> UiState.Failed(Reason.Network)
        is ImaginaNoMailboxesException -> UiState.Failed(Reason.NoMailboxes)
        is ImaginaSignInRequiredException -> UiState.Failed(Reason.SignInFailed)
        else -> UiState.Failed(Reason.Unknown)
    }

    private fun ImaginaApiException.reason(): Reason = when (code) {
        "no_seat" -> Reason.NoSeat
        "not_ready" -> Reason.NotReady
        "not_supported" -> Reason.NotSupported
        "unavailable" -> Reason.Unavailable
        else -> if (status >= SERVER_ERROR_STATUS) Reason.Unavailable else Reason.Unknown
    }

    private companion object {
        const val TAG = "ImaginaSignInViewModel"
        const val SERVER_ERROR_STATUS = 500
    }
}

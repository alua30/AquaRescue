package com.aquarescue.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aquarescue.app.R
import com.aquarescue.app.data.MonitoringRepository
import com.aquarescue.app.data.SettingsRepository
import com.aquarescue.app.model.AppSettings
import com.aquarescue.app.model.CommandResult
import com.aquarescue.app.model.MonitorState
import com.aquarescue.app.network.NetworkMonitor
import com.aquarescue.app.network.PhoneWifiState
import com.aquarescue.app.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние диалога SOS. */
sealed interface SosDialogState {
    data object Hidden : SosDialogState
    /** Запрос подтверждения у пользователя. [activate] = false — отмена SOS. */
    data class Confirm(val activate: Boolean) : SosDialogState
    data class Sending(val activate: Boolean) : SosDialogState
    data class Result(val activate: Boolean, val result: CommandResult) : SosDialogState
}

data class HomeUiState(
    val monitor: MonitorState = MonitorState(),
    val settings: AppSettings = AppSettings(),
    val wifi: PhoneWifiState = PhoneWifiState(false, false, null),
    val sosDialog: SosDialogState = SosDialogState.Hidden,
    val soundBusy: Boolean = false,
    val message: UiText? = null,
)

private data class HomeLocalState(
    val sosDialog: SosDialogState = SosDialogState.Hidden,
    val soundBusy: Boolean = false,
    val message: UiText? = null,
)

class HomeViewModel(
    private val monitoring: MonitoringRepository,
    settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val local = MutableStateFlow(HomeLocalState())

    val uiState: StateFlow<HomeUiState> = combine(
        monitoring.state, settingsRepository.settings, networkMonitor.wifiState, local,
    ) { monitor, settings, wifi, l ->
        HomeUiState(monitor, settings, wifi, l.sosDialog, l.soundBusy, l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Нажатие кнопки SOS: сначала подтверждение. */
    fun onSosClick() {
        val active = monitoring.state.value.snapshot?.sos?.active == true
        local.update { it.copy(sosDialog = SosDialogState.Confirm(activate = !active)) }
    }

    fun confirmSos(activate: Boolean) {
        local.update { it.copy(sosDialog = SosDialogState.Sending(activate)) }
        viewModelScope.launch {
            val result = monitoring.sendSos(activate)
            local.update { it.copy(sosDialog = SosDialogState.Result(activate, result)) }
        }
    }

    fun dismissSosDialog() {
        // Во время отправки диалог не закрывается — пользователь должен увидеть результат.
        if (local.value.sosDialog is SosDialogState.Sending) return
        local.update { it.copy(sosDialog = SosDialogState.Hidden) }
    }

    fun toggleDeviceSound() {
        if (local.value.soundBusy) return
        val muted = monitoring.state.value.snapshot?.audio?.muted ?: return
        local.update { it.copy(soundBusy = true) }
        viewModelScope.launch {
            val message = when (val r = monitoring.setDeviceMuted(!muted)) {
                is CommandResult.Confirmed -> UiText(
                    when {
                        r.value == true && r.demo -> R.string.home_sound_muted_demo
                        r.value == true -> R.string.home_sound_muted
                        r.demo -> R.string.home_sound_unmuted_demo
                        else -> R.string.home_sound_unmuted
                    }
                )
                is CommandResult.Failed -> UiText(R.string.home_sound_failed, r.error)
                is CommandResult.NotConnected -> UiText(R.string.home_sound_failed, r.error)
            }
            local.update { it.copy(soundBusy = false, message = message) }
        }
    }

    fun retryConnection() = monitoring.retryNow()

    fun messageShown() = local.update { it.copy(message = null) }
}

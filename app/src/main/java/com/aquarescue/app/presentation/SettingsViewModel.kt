package com.aquarescue.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.aquarescue.app.R
import com.aquarescue.app.data.MonitoringRepository
import com.aquarescue.app.data.SettingsRepository
import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.AppSettings
import com.aquarescue.app.network.toAppError
import com.aquarescue.app.ui.text.UiText
import com.aquarescue.app.model.DemoScenario
import com.aquarescue.app.model.DeviceInfo
import com.aquarescue.app.model.MonitorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionCheck {
    data object Idle : ConnectionCheck
    data class Running(val target: String) : ConnectionCheck
    data class Success(val target: String, val info: DeviceInfo) : ConnectionCheck
    data class Error(val target: String, val error: AppError) : ConnectionCheck
}

data class ConnectionForm(
    val host: String = "",
    val port: String = "",
    @param:StringRes val hostError: Int? = null,
    @param:StringRes val portError: Int? = null,
    /** Введённые значения отличаются от сохранённых. */
    val dirty: Boolean = false,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val monitor: MonitorState = MonitorState(),
    val form: ConnectionForm = ConnectionForm(),
    val check: ConnectionCheck = ConnectionCheck.Idle,
    val message: UiText? = null,
)

private data class SettingsLocal(
    val form: ConnectionForm = ConnectionForm(),
    val check: ConnectionCheck = ConnectionCheck.Idle,
    val message: UiText? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val monitoring: MonitoringRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsLocal())
    private var checkJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings, monitoring.state, local,
    ) { settings, monitor, l ->
        SettingsUiState(settings, monitor, l.form, l.check, l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            val s = settingsRepository.settingsFlow.first()
            local.update { it.copy(form = ConnectionForm(host = s.host, port = s.port.toString())) }
        }
    }

    fun onHostChange(value: String) = updateForm { it.copy(host = value.trim(), hostError = null) }

    fun onPortChange(value: String) =
        updateForm { it.copy(port = value.filter(Char::isDigit).take(5), portError = null) }

    private fun updateForm(transform: (ConnectionForm) -> ConnectionForm) {
        local.update { l ->
            val f = transform(l.form)
            val saved = settingsRepository.settings.value
            l.copy(form = f.copy(dirty = f.host != saved.host || f.port != saved.port.toString()))
        }
    }

    /** Проверяет форму; возвращает (host, port) или null с ошибками в форме. */
    private fun validate(): Pair<String, Int>? {
        val f = local.value.form
        val hostError = when {
            f.host.isBlank() -> R.string.settings_error_host_empty
            !HOST_REGEX.matches(f.host) -> R.string.settings_error_host_invalid
            IPV4_LIKE.matches(f.host) && !isValidIpv4(f.host) -> R.string.settings_error_ipv4_invalid
            else -> null
        }
        val port = f.port.toIntOrNull()
        val portError = if (port == null || port !in 1..65535) R.string.settings_error_port else null
        local.update { it.copy(form = it.form.copy(hostError = hostError, portError = portError)) }
        return if (hostError == null && portError == null && port != null) f.host to port else null
    }

    fun saveConnection() {
        val (host, port) = validate() ?: return
        viewModelScope.launch {
            settingsRepository.update { it.copy(host = host, port = port) }
            local.update { it.copy(form = it.form.copy(dirty = false), message = UiText(R.string.settings_address_saved, "$host:$port")) }
        }
    }

    /** Проверка связи с ESP32 по введённому адресу (реальный запрос GET /api/info). */
    fun checkConnection() {
        val (host, port) = validate() ?: return
        val target = "$host:$port"
        checkJob?.cancel()
        local.update { it.copy(check = ConnectionCheck.Running(target)) }
        checkJob = viewModelScope.launch {
            val result = monitoring.checkConnection(host, port)
            local.update {
                it.copy(
                    check = result.fold(
                        onSuccess = { info -> ConnectionCheck.Success(target, info) },
                        onFailure = { e -> ConnectionCheck.Error(target, e.toAppError()) },
                    )
                )
            }
        }
    }

    fun setDemoMode(enabled: Boolean) = updateSettings(
        UiText(if (enabled) R.string.settings_demo_enabled_msg else R.string.settings_demo_disabled_msg),
    ) { it.copy(demoMode = enabled, connectionEnabled = true) }

    fun setConnectionEnabled(enabled: Boolean) = updateSettings(null) { it.copy(connectionEnabled = enabled) }

    fun setPhoneAlarmOnDanger(enabled: Boolean) = updateSettings(null) { it.copy(phoneAlarmOnDanger = enabled) }

    fun setPhoneAlarmOnConnectionLost(enabled: Boolean) =
        updateSettings(null) { it.copy(phoneAlarmOnConnectionLost = enabled) }

    fun setWarningDepth(value: Double) = updateSettings(null) {
        it.copy(warningDepthM = value, dangerDepthM = maxOf(it.dangerDepthM, value + 0.5))
    }

    fun setDangerDepth(value: Double) = updateSettings(null) {
        it.copy(dangerDepthM = value, warningDepthM = minOf(it.warningDepthM, value - 0.5).coerceAtLeast(0.2))
    }

    fun setSubmersionLimit(sec: Int) = updateSettings(null) { it.copy(submersionLimitSec = sec) }

    fun setDemoScenario(scenario: DemoScenario) = updateSettings(null) { it.copy(demoScenario = scenario) }

    fun testPhoneAlarm() = monitoring.testPhoneAlarm()

    fun retryNow() = monitoring.retryNow()

    fun messageShown() = local.update { it.copy(message = null) }

    private fun updateSettings(message: UiText?, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            if (message != null) local.update { it.copy(message = message) }
        }
    }

    private fun isValidIpv4(host: String): Boolean =
        host.split('.').let { parts -> parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true } }

    private companion object {
        val HOST_REGEX = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$")
        val IPV4_LIKE = Regex("^[0-9.]+$")
    }
}

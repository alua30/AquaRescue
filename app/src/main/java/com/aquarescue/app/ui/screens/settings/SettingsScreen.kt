package com.aquarescue.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aquarescue.app.BuildConfig
import com.aquarescue.app.R
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DemoScenario
import com.aquarescue.app.model.SensorStatus
import com.aquarescue.app.network.Esp32JsonParser
import com.aquarescue.app.presentation.AppLanguage
import com.aquarescue.app.presentation.AppViewModelProvider
import com.aquarescue.app.presentation.ConnectionCheck
import com.aquarescue.app.presentation.SettingsUiState
import com.aquarescue.app.presentation.SettingsViewModel
import com.aquarescue.app.ui.components.CardHeader
import com.aquarescue.app.ui.components.DemoTag
import com.aquarescue.app.ui.components.InfoRow
import com.aquarescue.app.ui.components.MarineCard
import com.aquarescue.app.ui.components.SectionTitle
import com.aquarescue.app.ui.components.StatusPill
import com.aquarescue.app.ui.components.connectionColor
import com.aquarescue.app.ui.components.rememberNow
import com.aquarescue.app.ui.text.descriptionRes
import com.aquarescue.app.ui.text.message
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.ui.text.sensorTitle
import com.aquarescue.app.ui.text.titleRes
import com.aquarescue.app.ui.theme.MarineOutlineSoft
import com.aquarescue.app.ui.theme.Spacing
import com.aquarescue.app.ui.theme.StatusDanger
import com.aquarescue.app.ui.theme.StatusDemo
import com.aquarescue.app.ui.theme.StatusLost
import com.aquarescue.app.ui.theme.StatusNormal
import com.aquarescue.app.ui.theme.StatusWarning
import com.aquarescue.app.ui.theme.TextPrimary
import com.aquarescue.app.ui.theme.TextSecondary
import com.aquarescue.app.util.Fmt
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val res = resources()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.resolve(res))
            viewModel.messageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

            SectionTitle(stringResource(R.string.settings_section_language))
            LanguageCard()

            SectionTitle(stringResource(R.string.settings_section_mode))
            ModeCard(state, viewModel)

            SectionTitle(stringResource(R.string.settings_section_connection))
            ConnectionCard(state, viewModel)

            SectionTitle(stringResource(R.string.settings_section_sound))
            SoundCard(state, viewModel)

            SectionTitle(stringResource(R.string.settings_section_thresholds))
            ThresholdsCard(state, viewModel)

            SectionTitle(stringResource(R.string.settings_section_device))
            DeviceInfoCard(state)

            SectionTitle(stringResource(R.string.settings_section_about))
            AboutCard()
            Spacer(Modifier.size(Spacing.xl))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Spacing.l))
    }
}

// ------------------------------------------------------------------ Язык

@Composable
private fun LanguageCard() {
    // Выбор меняет язык сразу: Activity пересоздаётся с новой конфигурацией.
    var selected by remember { mutableStateOf(AppLanguage.current()) }
    MarineCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Language, stringResource(R.string.settings_language_header))
        Column(Modifier.selectableGroup().padding(top = Spacing.xs)) {
            AppLanguage.entries.forEach { language ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = selected == language,
                            onClick = {
                                if (selected != language) {
                                    selected = language
                                    AppLanguage.apply(language)
                                }
                            },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == language, onClick = null)
                    Spacer(Modifier.width(Spacing.m))
                    Text(stringResource(language.label), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Режим

@Composable
private fun ModeCard(state: SettingsUiState, vm: SettingsViewModel) {
    val demo = state.settings.demoMode
    MarineCard(Modifier.fillMaxWidth()) {
        SwitchRow(
            title = stringResource(R.string.settings_demo_title),
            subtitle = stringResource(if (demo) R.string.settings_demo_on else R.string.settings_demo_off),
            checked = demo,
            onCheckedChange = vm::setDemoMode,
        )
        if (demo) {
            HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MarineOutlineSoft)
            CardHeader(Icons.Rounded.Science, stringResource(R.string.settings_scenario_header), tint = StatusDemo)
            Spacer(Modifier.size(Spacing.xs))
            Column(Modifier.selectableGroup()) {
                DemoScenario.entries.forEach { scenario ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.settings.demoScenario == scenario,
                                onClick = { vm.setDemoScenario(scenario) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.settings.demoScenario == scenario, onClick = null)
                        Spacer(Modifier.width(Spacing.m))
                        Column {
                            Text(stringResource(scenario.titleRes()), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text(stringResource(scenario.descriptionRes()), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.size(Spacing.xs))
            Text(stringResource(R.string.settings_demo_note), style = MaterialTheme.typography.bodySmall, color = StatusDemo)
        }
    }
}

// ---------------------------------------------------------- Подключение

@Composable
private fun ConnectionCard(state: SettingsUiState, vm: SettingsViewModel) {
    val focus = LocalFocusManager.current
    val res = resources()
    val monitor = state.monitor
    val demo = state.settings.demoMode
    val now = rememberNow()

    MarineCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Router, stringResource(R.string.settings_connection_header))
        Spacer(Modifier.size(Spacing.s))
        // Статус отдельной строкой: длинные переводы не теснят заголовок карточки.
        if (demo) {
            StatusPill(stringResource(R.string.settings_not_used), StatusLost)
        } else {
            StatusPill(stringResource(monitor.connection.titleRes()), connectionColor(monitor.connection))
        }
        Spacer(Modifier.size(Spacing.s))
        if (!demo) {
            monitor.endpointLabel?.let { InfoText(stringResource(R.string.settings_address, it)) }
            monitor.lastUpdateAt?.let { InfoText(stringResource(R.string.settings_last_data, Fmt.ago(res, it, now))) }
            if (monitor.connection != ConnectionStatus.CONNECTED) {
                monitor.lastError?.let { InfoText(stringResource(R.string.settings_error, it.message(res))) }
            }
            if (monitor.connection == ConnectionStatus.LOST || monitor.connection == ConnectionStatus.RECONNECTING) {
                TextButton(onClick = vm::retryNow, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.settings_reconnect_now))
                }
            }
        } else {
            InfoText(stringResource(R.string.settings_demo_connection_hint))
        }
        Spacer(Modifier.size(Spacing.m))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            OutlinedTextField(
                value = state.form.host,
                onValueChange = vm::onHostChange,
                label = { Text(stringResource(R.string.settings_ip_label), maxLines = 1) },
                placeholder = { Text("192.168.4.1") },
                isError = state.form.hostError != null,
                supportingText = state.form.hostError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.form.port,
                onValueChange = vm::onPortChange,
                label = { Text(stringResource(R.string.settings_port_label), maxLines = 1) },
                isError = state.form.portError != null,
                supportingText = state.form.portError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.saveConnection() }),
                modifier = Modifier.width(104.dp),
            )
        }
        Spacer(Modifier.size(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Button(
                onClick = { focus.clearFocus(); vm.saveConnection() },
                enabled = state.form.dirty,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.common_save), maxLines = 1) }
            OutlinedButton(
                onClick = { focus.clearFocus(); vm.checkConnection() },
                enabled = state.check !is ConnectionCheck.Running,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_check), maxLines = 1) }
        }
        CheckResult(state.check)

        HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MarineOutlineSoft)
        SwitchRow(
            title = stringResource(R.string.settings_connection_active),
            subtitle = stringResource(
                if (state.settings.connectionEnabled) R.string.settings_connection_active_on else R.string.settings_connection_active_off
            ),
            checked = state.settings.connectionEnabled,
            onCheckedChange = vm::setConnectionEnabled,
        )
        Text(
            stringResource(R.string.settings_connection_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
}

@Composable
private fun CheckResult(check: ConnectionCheck) {
    val res = resources()
    when (check) {
        ConnectionCheck.Idle -> Unit
        is ConnectionCheck.Running -> Row(Modifier.padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.s))
            InfoText(stringResource(R.string.settings_check_running, check.target))
        }
        is ConnectionCheck.Success -> Column(Modifier.padding(top = Spacing.m)) {
            StatusPill(stringResource(R.string.settings_check_ok), StatusNormal)
            Spacer(Modifier.size(Spacing.xs))
            Text(
                listOfNotNull(
                    check.info.deviceName,
                    check.info.firmwareVersion?.let { stringResource(R.string.settings_check_firmware, it) },
                    stringResource(R.string.settings_check_api, check.info.apiVersion),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
            )
        }
        is ConnectionCheck.Error -> Column(Modifier.padding(top = Spacing.m)) {
            StatusPill(stringResource(R.string.settings_check_failed, check.target), StatusDanger)
            Spacer(Modifier.size(Spacing.xs))
            InfoText(check.error.message(res))
        }
    }
}

// ------------------------------------------------------------------ Звук

@Composable
private fun SoundCard(state: SettingsUiState, vm: SettingsViewModel) {
    MarineCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.NotificationsActive, stringResource(R.string.settings_sound_header))
        SwitchRow(
            title = stringResource(R.string.settings_alarm_danger),
            subtitle = stringResource(R.string.settings_alarm_danger_desc),
            checked = state.settings.phoneAlarmOnDanger,
            onCheckedChange = vm::setPhoneAlarmOnDanger,
        )
        SwitchRow(
            title = stringResource(R.string.settings_alarm_lost),
            subtitle = stringResource(R.string.settings_alarm_lost_desc),
            checked = state.settings.phoneAlarmOnConnectionLost,
            onCheckedChange = vm::setPhoneAlarmOnConnectionLost,
        )
        OutlinedButton(onClick = vm::testPhoneAlarm, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s)) {
            Text(stringResource(R.string.settings_test_sound))
        }
        Text(
            stringResource(R.string.settings_sound_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

// -------------------------------------------------------------- Пороги

@Composable
private fun ThresholdsCard(state: SettingsUiState, vm: SettingsViewModel) {
    val s = state.settings
    val res = resources()
    MarineCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Tune, stringResource(R.string.settings_thresholds_header))
        SliderRow(
            title = stringResource(R.string.settings_threshold_warning),
            format = { Fmt.depthShort(res, it.toDouble()) },
            value = s.warningDepthM.toFloat(),
            range = 0.2f..3.0f,
            steps = 27,
            color = StatusWarning,
            onChange = { vm.setWarningDepth(roundTenth(it)) },
        )
        SliderRow(
            title = stringResource(R.string.settings_threshold_danger),
            format = { Fmt.depthShort(res, it.toDouble()) },
            value = s.dangerDepthM.toFloat(),
            range = 0.7f..5.0f,
            steps = 42,
            color = StatusDanger,
            onChange = { vm.setDangerDepth(roundTenth(it)) },
        )
        SliderRow(
            title = stringResource(R.string.settings_threshold_time),
            format = { Fmt.seconds(res, it.roundToInt()) },
            value = s.submersionLimitSec.toFloat(),
            range = 5f..60f,
            steps = 10,
            color = StatusDanger,
            onChange = { vm.setSubmersionLimit(it.roundToInt()) },
        )
    }
}

private fun roundTenth(v: Float): Double = (v * 10).roundToInt() / 10.0

@Composable
private fun SliderRow(
    title: String,
    format: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    color: Color,
    onChange: (Float) -> Unit,
) {
    // Значение меняется локально во время перетаскивания и сохраняется при отпускании.
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Column(Modifier.padding(top = Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f).padding(end = Spacing.s),
            )
            Text(format(current), style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

// ---------------------------------------------------------- Устройство

private val SENSOR_KEYS = listOf("ms5837", "gps", "dfplayer", "mic", "sos_button")

@Composable
private fun DeviceInfoCard(state: SettingsUiState) {
    val res = resources()
    val monitor = state.monitor
    val info = monitor.deviceInfo
    val snapshot = monitor.snapshot
    MarineCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Memory, stringResource(R.string.settings_device_header)) {
            if (monitor.source == DataSource.DEMO) DemoTag()
        }
        Spacer(Modifier.size(Spacing.s))
        if (info == null && snapshot == null) {
            Text(
                stringResource(if (monitor.source == DataSource.DEMO) R.string.settings_device_waiting_demo else R.string.settings_device_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else {
            info?.deviceName?.let { InfoRow(stringResource(R.string.settings_device_name), it) }
            (info?.deviceId ?: snapshot?.deviceId)?.let { InfoRow(stringResource(R.string.settings_device_id), it) }
            info?.firmwareVersion?.let { InfoRow(stringResource(R.string.settings_device_firmware), it) }
            info?.chip?.let { InfoRow(stringResource(R.string.settings_device_chip), it) }
            info?.mac?.let { InfoRow(stringResource(R.string.settings_device_mac), it) }
            (info?.ip ?: snapshot?.wifi?.ip)?.let { InfoRow(stringResource(R.string.settings_device_ip), it) }
            (snapshot?.wifi?.mode ?: info?.wifiMode)?.let { mode ->
                InfoRow(
                    stringResource(R.string.settings_device_wifi_mode),
                    when (mode) {
                        "ap" -> stringResource(R.string.wifi_mode_ap)
                        "sta" -> stringResource(R.string.wifi_mode_sta)
                        "demo" -> stringResource(R.string.wifi_mode_demo)
                        else -> mode
                    },
                )
            }
            snapshot?.wifi?.rssi?.let { InfoRow(stringResource(R.string.settings_device_rssi), Fmt.rssi(res, it)) }
            snapshot?.uptimeMs?.let { InfoRow(stringResource(R.string.settings_device_uptime), Fmt.duration(res, it)) }
            InfoRow(
                stringResource(R.string.settings_device_api),
                (snapshot?.apiVersion ?: info?.apiVersion)?.toString() ?: stringResource(R.string.common_dash),
            )

            if (snapshot != null) {
                HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MarineOutlineSoft)
                Text(stringResource(R.string.settings_modules_title), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                SENSOR_KEYS.forEach { key ->
                    val status = snapshot.sensors[key] ?: SensorStatus.UNKNOWN
                    val (text, color) = when (status) {
                        SensorStatus.OK -> R.string.sensor_status_ok to StatusNormal
                        SensorStatus.NO_FIX -> R.string.sensor_status_no_fix to StatusWarning
                        SensorStatus.ERROR -> R.string.sensor_status_error to StatusDanger
                        SensorStatus.ABSENT -> R.string.sensor_status_absent to StatusLost
                        SensorStatus.UNKNOWN -> R.string.sensor_status_unknown to StatusLost
                    }
                    InfoRow(
                        sensorTitle(res, key).replaceFirstChar { it.titlecase(Fmt.locale(res)) },
                        stringResource(text),
                        valueColor = color,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ О приложении

@Composable
private fun AboutCard() {
    MarineCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Info, stringResource(R.string.app_name))
        Spacer(Modifier.size(Spacing.s))
        InfoRow(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoRow(stringResource(R.string.about_protocol), stringResource(R.string.about_protocol_value, Esp32JsonParser.SUPPORTED_API_VERSION))
        InfoRow(
            stringResource(R.string.about_build),
            stringResource(if (BuildConfig.DEBUG) R.string.about_build_debug else R.string.about_build_release),
        )
        Text(
            stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

// ---------------------------------------------------------------- Общее

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = checked, onClick = { onCheckedChange(!checked) }, role = Role.Switch)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

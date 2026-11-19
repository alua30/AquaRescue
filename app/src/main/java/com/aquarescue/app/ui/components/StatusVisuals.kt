package com.aquarescue.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aquarescue.app.R
import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.MonitorState
import com.aquarescue.app.ui.theme.MarinePrimary
import com.aquarescue.app.ui.theme.StatusDanger
import com.aquarescue.app.ui.theme.StatusLost
import com.aquarescue.app.ui.theme.StatusNormal
import com.aquarescue.app.ui.theme.StatusWarning

/**
 * Визуальное представление состояния системы.
 * [code] — технический код (NORMAL, WARNING…) из strings.xml, не переводится.
 */
data class StatusVisual(
    @param:StringRes val code: Int,
    @param:StringRes val title: Int,
    val color: Color,
    val icon: ImageVector,
)

fun alarmVisual(state: AlarmState): StatusVisual = when (state) {
    AlarmState.NORMAL -> StatusVisual(R.string.code_normal, R.string.status_normal, StatusNormal, Icons.Rounded.CheckCircle)
    AlarmState.WARNING -> StatusVisual(R.string.code_warning, R.string.status_warning, StatusWarning, Icons.Rounded.Warning)
    AlarmState.DANGER -> StatusVisual(R.string.code_danger, R.string.status_danger, StatusDanger, Icons.Rounded.Error)
    AlarmState.CONNECTION_LOST ->
        StatusVisual(R.string.code_connection_lost, R.string.status_connection_lost, StatusLost, Icons.Rounded.CloudOff)
}

/** Состояние системы с учётом случая «данных ещё нет» (не выдаём это за норму). */
fun systemVisual(state: MonitorState): StatusVisual = when {
    state.alarm.state == AlarmState.CONNECTION_LOST -> alarmVisual(AlarmState.CONNECTION_LOST)
    state.connection == ConnectionStatus.DISABLED ->
        StatusVisual(R.string.code_offline, R.string.status_offline, StatusLost, Icons.Rounded.LinkOff)
    !state.hasData -> StatusVisual(R.string.code_waiting, R.string.status_waiting, MarinePrimary, Icons.Rounded.HourglassTop)
    else -> alarmVisual(state.alarm.state)
}

fun connectionColor(status: ConnectionStatus): Color = when (status) {
    ConnectionStatus.CONNECTED -> StatusNormal
    ConnectionStatus.CONNECTING -> MarinePrimary
    ConnectionStatus.RECONNECTING -> StatusWarning
    ConnectionStatus.LOST -> StatusDanger
    ConnectionStatus.DISABLED -> StatusLost
}

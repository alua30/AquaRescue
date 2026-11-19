package com.aquarescue.app.ui.text

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.aquarescue.app.R
import com.aquarescue.app.model.AlarmReason
import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DemoScenario
import com.aquarescue.app.model.EventCategory
import com.aquarescue.app.model.EventType
import com.aquarescue.app.model.HistoryEvent
import com.aquarescue.app.util.Fmt
import kotlin.math.roundToInt

/**
 * Единая точка превращения структурированных данных (ошибки, причины тревоги,
 * события истории) в текст на текущем языке интерфейса.
 */

/** Resources текущей конфигурации; пересчитывается при смене языка. */
@Composable
@ReadOnlyComposable
fun resources(): Resources = LocalResources.current

/** Текст для одноразовых сообщений (снекбары) из ViewModel. */
class UiText(@param:StringRes val id: Int, vararg val args: Any) {
    fun resolve(res: Resources): String {
        val resolved = args.map { arg ->
            when (arg) {
                is UiText -> arg.resolve(res)
                is AppError -> arg.message(res)
                else -> arg
            }
        }.toTypedArray()
        return res.getString(id, *resolved)
    }
}

// ------------------------------------------------------------------ Ошибки

fun AppError.message(res: Resources): String = when (code) {
    AppError.Code.TIMEOUT -> res.getString(R.string.err_timeout)
    AppError.Code.CONNECTION_REFUSED -> res.getString(R.string.err_connection_refused)
    AppError.Code.NO_ROUTE -> res.getString(R.string.err_no_route)
    AppError.Code.UNKNOWN_HOST -> res.getString(R.string.err_unknown_host)
    AppError.Code.NETWORK -> res.getString(R.string.err_network, arg.orEmpty())
    AppError.Code.UNKNOWN -> res.getString(R.string.err_unknown, arg.orEmpty())
    AppError.Code.INVALID_ADDRESS -> res.getString(R.string.err_invalid_address, arg.orEmpty())
    AppError.Code.INVALID_JSON -> res.getString(R.string.err_invalid_json)
    AppError.Code.NOT_AQUARESCUE -> res.getString(R.string.err_not_aquarescue)
    AppError.Code.MISSING_BLOCK -> res.getString(R.string.err_missing_block, arg.orEmpty())
    AppError.Code.UNSUPPORTED_API -> res.getString(R.string.err_unsupported_api, arg.orEmpty(), arg2.orEmpty())
    AppError.Code.HTTP_ERROR -> if (arg2.isNullOrBlank()) {
        res.getString(R.string.err_http, arg.orEmpty())
    } else {
        res.getString(R.string.err_http_with_reason, arg.orEmpty(), arg2)
    }
    AppError.Code.SOS_REJECTED -> res.getString(R.string.err_sos_rejected)
    AppError.Code.SOS_WRONG_REQUEST_ID -> res.getString(R.string.err_sos_wrong_id)
    AppError.Code.SOS_STATE_UNCHANGED -> res.getString(R.string.err_sos_state_unchanged)
    AppError.Code.SOUND_NOT_CONFIRMED -> res.getString(R.string.err_sound_not_confirmed)
    AppError.Code.DEMO_CONNECTION_LOST -> res.getString(R.string.err_demo_connection_lost)
    AppError.Code.DEMO_SOS_NO_RESPONSE -> res.getString(R.string.err_demo_sos_no_response)
    AppError.Code.DEMO_NO_RESPONSE -> res.getString(R.string.err_demo_no_response)
    AppError.Code.CONNECTION_DISABLED -> res.getString(R.string.err_connection_disabled)
    AppError.Code.NO_CONNECTION -> res.getString(R.string.err_no_connection)
}

// ---------------------------------------------------------- Причины тревоги

fun AlarmReason.text(res: Resources): String = when (kind) {
    AlarmReason.Kind.NO_DATA -> res.getString(R.string.reason_no_data, (a ?: 0.0).roundToInt())
    AlarmReason.Kind.SOS_BUTTON -> res.getString(R.string.reason_sos_button)
    AlarmReason.Kind.SOS_APP -> res.getString(R.string.reason_sos_app)
    AlarmReason.Kind.SOS_ACTIVE -> res.getString(R.string.reason_sos_active)
    AlarmReason.Kind.DEPTH_DANGER ->
        res.getString(R.string.reason_depth_danger, Fmt.depth(res, a ?: 0.0), Fmt.depth(res, b ?: 0.0))
    AlarmReason.Kind.SUBMERGED_TOO_LONG -> res.getString(R.string.reason_submerged, (a ?: 0.0).roundToInt())
    AlarmReason.Kind.DEPTH_WARNING ->
        res.getString(R.string.reason_depth_warning, Fmt.depth(res, a ?: 0.0), Fmt.depth(res, b ?: 0.0))
    AlarmReason.Kind.DEPTH_SENSOR_UNAVAILABLE -> res.getString(R.string.reason_depth_sensor)
    AlarmReason.Kind.GPS_MODULE_ERROR -> res.getString(R.string.reason_gps_error)
    AlarmReason.Kind.DFPLAYER_ERROR -> res.getString(R.string.reason_dfplayer)
    AlarmReason.Kind.SOS_BUTTON_FAULT -> res.getString(R.string.reason_sos_button_fault)
    AlarmReason.Kind.DEVICE_DANGER -> res.getString(R.string.reason_device_danger)
    AlarmReason.Kind.DEVICE_WARNING -> res.getString(R.string.reason_device_warning)
}

// --------------------------------------------------------- События истории

fun HistoryEvent.title(res: Resources): String = when (type) {
    EventType.DEPTH_CHANGE -> res.getString(R.string.event_depth, Fmt.depth(res, depthM ?: 0.0))
    EventType.GPS_FIX_ACQUIRED -> res.getString(R.string.event_gps_acquired)
    EventType.GPS_FIX_LOST -> res.getString(R.string.event_gps_lost)
    EventType.GPS_UPDATE -> res.getString(R.string.event_gps_update, Fmt.metersInt(res, (value ?: 0.0).toInt()))
    EventType.SOS_DEVICE -> res.getString(R.string.event_sos_device)
    EventType.SOS_SENT -> res.getString(if (source == DataSource.DEMO) R.string.event_sos_sent_demo else R.string.event_sos_sent)
    EventType.SOS_FAILED -> res.getString(
        when {
            !flag -> R.string.event_sos_cancel_failed
            error?.code == AppError.Code.CONNECTION_DISABLED -> R.string.event_sos_not_sent
            else -> R.string.event_sos_failed
        }
    )
    EventType.SOS_CANCELLED ->
        res.getString(if (source == DataSource.DEMO) R.string.event_sos_cancelled_demo else R.string.event_sos_cancelled)
    EventType.SOS_CLEARED -> res.getString(R.string.event_sos_cleared)
    EventType.WARNING -> res.getString(R.string.event_warning)
    EventType.DANGER -> res.getString(R.string.event_danger)
    EventType.NORMAL_RESTORED -> res.getString(R.string.event_normal_restored)
    EventType.SENSOR_FAULT -> res.getString(R.string.event_sensor_fault, sensorTitle(res, text.orEmpty()))
    // Отдельные строки для демо и ESP32: падежные формы различаются (рус. «с демо-устройством», каз. «құрылғымен»).
    EventType.CONNECTION_LOST -> res.getString(
        when {
            flag && source == DataSource.DEMO -> R.string.event_connect_failed_demo
            flag -> R.string.event_connect_failed_device
            source == DataSource.DEMO -> R.string.event_connection_lost_demo
            else -> R.string.event_connection_lost_device
        }
    )
    EventType.CONNECTION_RESTORED -> res.getString(R.string.event_connection_restored)
    EventType.CONNECTED ->
        res.getString(if (source == DataSource.DEMO) R.string.event_connected_demo else R.string.event_connected_device)
}

fun HistoryEvent.details(res: Resources): String? {
    val coords = if (lat != null && lon != null) Fmt.coordinates(lat, lon) else null
    return when (type) {
        EventType.DEPTH_CHANGE -> value?.let {
            res.getString(if (flag) R.string.event_depth_details_up else R.string.event_depth_details_down, Fmt.depth(res, it))
        }
        EventType.GPS_FIX_ACQUIRED -> coords?.let { c ->
            if (count != null) res.getString(R.string.event_gps_satellites, c, count) else c
        }
        EventType.GPS_UPDATE -> coords
        EventType.GPS_FIX_LOST -> if (coords != null) {
            res.getString(R.string.event_gps_lost_details, coords)
        } else {
            res.getString(R.string.event_gps_lost_details_nocoords)
        }
        EventType.SOS_DEVICE, EventType.SOS_SENT -> coords?.let { res.getString(R.string.event_last_coords, it) }
        EventType.SOS_FAILED -> error?.message(res)
        EventType.WARNING, EventType.DANGER -> reasons.joinToString("; ") { it.text(res) }.ifBlank { null }
        EventType.SENSOR_FAULT -> res.getString(R.string.event_sensor_fault_details)
        EventType.CONNECTION_LOST -> error?.message(res)
        EventType.CONNECTION_RESTORED -> res.getString(
            if (source == DataSource.DEMO) R.string.event_connection_restored_details_demo else R.string.event_connection_restored_details_device
        )
        EventType.CONNECTED -> text
        EventType.SOS_CANCELLED, EventType.SOS_CLEARED, EventType.NORMAL_RESTORED -> null
    }
}

// ------------------------------------------------------------ Справочники

@StringRes
fun sensorTitleRes(key: String): Int? = when (key) {
    "ms5837" -> R.string.sensor_ms5837
    "gps" -> R.string.sensor_gps
    "dfplayer" -> R.string.sensor_dfplayer
    "mic" -> R.string.sensor_mic
    "sos_button" -> R.string.sensor_sos_button
    else -> null
}

fun sensorTitle(res: Resources, key: String): String = sensorTitleRes(key)?.let(res::getString) ?: key

@StringRes
fun EventCategory.titleRes(): Int = when (this) {
    EventCategory.ALARM -> R.string.category_alarm
    EventCategory.SOS -> R.string.category_sos
    EventCategory.DEPTH -> R.string.category_depth
    EventCategory.GPS -> R.string.category_gps
    EventCategory.CONNECTION -> R.string.category_connection
}

@StringRes
fun DemoScenario.titleRes(): Int = when (this) {
    DemoScenario.AUTO -> R.string.scenario_auto
    DemoScenario.NORMAL -> R.string.scenario_normal
    DemoScenario.WARNING -> R.string.scenario_warning
    DemoScenario.DANGER -> R.string.scenario_danger
    DemoScenario.SENSOR_FAULT -> R.string.scenario_sensor_fault
    DemoScenario.CONNECTION_LOST -> R.string.scenario_connection_lost
}

@StringRes
fun DemoScenario.descriptionRes(): Int = when (this) {
    DemoScenario.AUTO -> R.string.scenario_auto_desc
    DemoScenario.NORMAL -> R.string.scenario_normal_desc
    DemoScenario.WARNING -> R.string.scenario_warning_desc
    DemoScenario.DANGER -> R.string.scenario_danger_desc
    DemoScenario.SENSOR_FAULT -> R.string.scenario_sensor_fault_desc
    DemoScenario.CONNECTION_LOST -> R.string.scenario_connection_lost_desc
}

@StringRes
fun ConnectionStatus.titleRes(): Int = when (this) {
    ConnectionStatus.DISABLED -> R.string.conn_disabled
    ConnectionStatus.CONNECTING -> R.string.conn_connecting
    ConnectionStatus.CONNECTED -> R.string.conn_connected
    ConnectionStatus.RECONNECTING -> R.string.conn_reconnecting
    ConnectionStatus.LOST -> R.string.conn_lost
}

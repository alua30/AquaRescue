package com.aquarescue.app.domain

import com.aquarescue.app.model.AlarmEvaluation
import com.aquarescue.app.model.AlarmReason
import com.aquarescue.app.model.AlarmReason.Kind
import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.AppSettings
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.SensorStatus
import com.aquarescue.app.model.SosSource
import com.aquarescue.app.model.TelemetrySnapshot

/**
 * Бизнес-правила тревоги (docs/ESP32_API.md, раздел 5).
 * Чистая функция без зависимостей от Android. Причины возвращаются в виде
 * [AlarmReason] — текст на нужном языке формирует интерфейс.
 */
class AlarmEvaluator {

    fun evaluate(
        snapshot: TelemetrySnapshot?,
        connection: ConnectionStatus,
        settings: AppSettings,
        submergedSince: Long?,
        now: Long,
    ): AlarmEvaluation {
        if (connection == ConnectionStatus.LOST) {
            return AlarmEvaluation(
                AlarmState.CONNECTION_LOST,
                listOf(AlarmReason(Kind.NO_DATA, a = (LOST_TIMEOUT_MS / 1000).toDouble())),
            )
        }
        if (snapshot == null) return AlarmEvaluation(AlarmState.NORMAL, emptyList())

        val danger = mutableListOf<AlarmReason>()
        val warning = mutableListOf<AlarmReason>()

        if (snapshot.sos.active) {
            danger += AlarmReason(
                when (snapshot.sos.source) {
                    SosSource.BUTTON -> Kind.SOS_BUTTON
                    SosSource.APP -> Kind.SOS_APP
                    SosSource.NONE -> Kind.SOS_ACTIVE
                }
            )
        }

        val depth = snapshot.depth.depthM
        if (snapshot.depth.valid && depth != null) {
            val submergedSec = submergedSince?.let { (now - it) / 1000 } ?: 0
            when {
                depth >= settings.dangerDepthM ->
                    danger += AlarmReason(Kind.DEPTH_DANGER, depth, settings.dangerDepthM)
                depth >= settings.warningDepthM && submergedSec >= settings.submersionLimitSec ->
                    danger += AlarmReason(Kind.SUBMERGED_TOO_LONG, settings.submersionLimitSec.toDouble())
                depth >= settings.warningDepthM ->
                    warning += AlarmReason(Kind.DEPTH_WARNING, depth, settings.warningDepthM)
            }
        } else {
            warning += AlarmReason(Kind.DEPTH_SENSOR_UNAVAILABLE)
        }

        if (snapshot.sensors["gps"] == SensorStatus.ERROR) warning += AlarmReason(Kind.GPS_MODULE_ERROR)
        if (snapshot.sensors["dfplayer"] == SensorStatus.ERROR || snapshot.audio.dfPlayerOk == false) {
            warning += AlarmReason(Kind.DFPLAYER_ERROR)
        }
        if (snapshot.sensors["sos_button"] == SensorStatus.ERROR) warning += AlarmReason(Kind.SOS_BUTTON_FAULT)

        when (snapshot.deviceAlarm) {
            AlarmState.DANGER -> danger += AlarmReason(Kind.DEVICE_DANGER)
            AlarmState.WARNING -> warning += AlarmReason(Kind.DEVICE_WARNING)
            else -> Unit
        }

        return when {
            danger.isNotEmpty() -> AlarmEvaluation(AlarmState.DANGER, danger + warning)
            warning.isNotEmpty() -> AlarmEvaluation(AlarmState.WARNING, warning)
            else -> AlarmEvaluation(AlarmState.NORMAL, emptyList())
        }
    }

    companion object {
        /** Через сколько миллисекунд без данных соединение считается потерянным. */
        const val LOST_TIMEOUT_MS = 6000L
    }
}

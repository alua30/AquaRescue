package com.aquarescue.app.data.source

import com.aquarescue.app.model.DeviceInfo
import com.aquarescue.app.model.TelemetrySnapshot

/**
 * Источник телеметрии. Есть две реализации, которые никогда не работают одновременно:
 * [DeviceTelemetrySource] — реальный ESP32 по HTTP, [DemoTelemetrySource] — генератор демо-данных.
 * Все методы бросают исключение, если устройство не ответило или не подтвердило команду.
 */
interface TelemetrySource {
    suspend fun fetchStatus(): TelemetrySnapshot
    suspend fun fetchInfo(): DeviceInfo

    /** @return true, если устройство подтвердило новое состояние SOS. */
    suspend fun sendSos(activate: Boolean): Boolean

    /** @return подтверждённое устройством состояние «звук выключен». */
    suspend fun setMuted(muted: Boolean): Boolean
}

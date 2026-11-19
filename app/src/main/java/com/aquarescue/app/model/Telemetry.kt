package com.aquarescue.app.model

/**
 * Один «снимок» телеметрии жилета. Формат соответствует GET /api/status (docs/ESP32_API.md).
 * [source] явно указывает происхождение данных: демонстрационные значения никогда
 * не смешиваются с данными реального устройства.
 */
data class TelemetrySnapshot(
    val source: DataSource,
    val apiVersion: Int,
    val deviceId: String?,
    val uptimeMs: Long?,
    val depth: DepthData,
    val gps: GpsData,
    val sos: SosData,
    val audio: AudioData,
    val sensors: Map<String, SensorStatus>,
    val wifi: DeviceWifi?,
    val deviceAlarm: AlarmState?,
    /** Время получения снимка телефоном (System.currentTimeMillis). */
    val receivedAt: Long,
)

data class DepthData(
    val valid: Boolean,
    val depthM: Double?,
    val pressureMbar: Double?,
    val temperatureC: Double?,
)

data class GpsData(
    /** Координаты хотя бы раз получены. */
    val valid: Boolean,
    /** Фиксация есть прямо сейчас. Под водой NEO-6M фиксацию теряет. */
    val fix: Boolean,
    val lat: Double?,
    val lon: Double?,
    val satellites: Int?,
    val hdop: Double?,
    val ageMs: Long?,
)

data class SosData(
    val active: Boolean,
    val source: SosSource,
    val sinceMs: Long?,
)

enum class SosSource { NONE, BUTTON, APP;
    companion object {
        fun parse(value: String?): SosSource = when (value?.lowercase()) {
            "button" -> BUTTON
            "app" -> APP
            else -> NONE
        }
    }
}

data class AudioData(
    val muted: Boolean,
    val dfPlayerOk: Boolean?,
    val buzzerOn: Boolean?,
)

data class DeviceWifi(
    val mode: String?,
    val rssi: Int?,
    val ip: String?,
)

enum class SensorStatus { OK, ERROR, ABSENT, NO_FIX, UNKNOWN;
    companion object {
        fun parse(value: String?): SensorStatus = when (value?.lowercase()) {
            "ok" -> OK
            "error" -> ERROR
            "absent" -> ABSENT
            "no_fix" -> NO_FIX
            else -> UNKNOWN
        }
    }
}

/** Точка с координатами, которую телефон получил от устройства. */
data class GeoFix(
    val lat: Double,
    val lon: Double,
    /** Время, когда фиксация была актуальна (по часам телефона). */
    val fixTime: Long,
    val satellites: Int?,
    val hdop: Double?,
    val source: DataSource,
)

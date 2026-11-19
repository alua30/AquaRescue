package com.aquarescue.app.network

import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.AudioData
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DepthData
import com.aquarescue.app.model.DeviceInfo
import com.aquarescue.app.model.DeviceWifi
import com.aquarescue.app.model.GpsData
import com.aquarescue.app.model.SensorStatus
import com.aquarescue.app.model.SosData
import com.aquarescue.app.model.SosSource
import com.aquarescue.app.model.TelemetrySnapshot
import org.json.JSONException
import org.json.JSONObject

/**
 * Разбор JSON-ответов ESP32 по контракту docs/ESP32_API.md.
 * Неизвестные поля игнорируются, отсутствующие необязательные поля превращаются в null.
 */
object Esp32JsonParser {

    const val SUPPORTED_API_VERSION = 1

    fun parseStatus(body: String, receivedAt: Long): TelemetrySnapshot {
        val root = parseObject(body)
        val apiVersion = root.optInt("api_version", -1)
        checkApiVersion(apiVersion)

        val depthObj = root.optJSONObject("depth")
            ?: throw Esp32Exception(AppError.Code.MISSING_BLOCK, "depth")
        val gpsObj = root.optJSONObject("gps")
            ?: throw Esp32Exception(AppError.Code.MISSING_BLOCK, "gps")
        val sosObj = root.optJSONObject("sos")
            ?: throw Esp32Exception(AppError.Code.MISSING_BLOCK, "sos")
        val audioObj = root.optJSONObject("audio")
        val sensorsObj = root.optJSONObject("sensors")
        val wifiObj = root.optJSONObject("wifi")

        val depthValid = depthObj.optBoolean("valid", false)
        val gpsValid = gpsObj.optBoolean("valid", false)
        val lat = gpsObj.optDoubleOrNull("lat")
        val lon = gpsObj.optDoubleOrNull("lon")
        val hasCoords = gpsValid && lat != null && lon != null && isValidCoordinate(lat, lon)

        val sensors = buildMap {
            sensorsObj?.keys()?.forEach { key ->
                put(key, SensorStatus.parse(sensorsObj.optString(key, "")))
            }
        }

        return TelemetrySnapshot(
            source = DataSource.DEVICE,
            apiVersion = apiVersion,
            deviceId = root.optStringOrNull("device_id"),
            uptimeMs = root.optLongOrNull("uptime_ms"),
            depth = DepthData(
                valid = depthValid,
                depthM = if (depthValid) depthObj.optDoubleOrNull("depth_m")?.coerceAtLeast(0.0) else null,
                pressureMbar = depthObj.optDoubleOrNull("pressure_mbar"),
                temperatureC = depthObj.optDoubleOrNull("temperature_c"),
            ),
            gps = GpsData(
                valid = hasCoords,
                fix = hasCoords && gpsObj.optBoolean("fix", false),
                lat = if (hasCoords) lat else null,
                lon = if (hasCoords) lon else null,
                satellites = gpsObj.optIntOrNull("satellites"),
                hdop = gpsObj.optDoubleOrNull("hdop"),
                ageMs = gpsObj.optLongOrNull("age_ms"),
            ),
            sos = SosData(
                active = sosObj.optBoolean("active", false),
                source = SosSource.parse(sosObj.optStringOrNull("source")),
                sinceMs = sosObj.optLongOrNull("since_ms"),
            ),
            audio = AudioData(
                muted = audioObj?.optBoolean("muted", false) ?: false,
                dfPlayerOk = audioObj?.optBooleanOrNull("dfplayer_ok"),
                buzzerOn = audioObj?.optBooleanOrNull("buzzer_on"),
            ),
            sensors = sensors,
            wifi = wifiObj?.let {
                DeviceWifi(
                    mode = it.optStringOrNull("mode"),
                    rssi = it.optIntOrNull("rssi")?.takeIf { rssi -> rssi < 0 },
                    ip = it.optStringOrNull("ip"),
                )
            },
            deviceAlarm = AlarmState.parseDevice(root.optStringOrNull("alarm")),
            receivedAt = receivedAt,
        )
    }

    fun parseInfo(body: String): DeviceInfo {
        val root = parseObject(body)
        val apiVersion = root.optInt("api_version", -1)
        checkApiVersion(apiVersion)
        val componentsArr = root.optJSONArray("components")
        val components = buildList {
            if (componentsArr != null) {
                for (i in 0 until componentsArr.length()) add(componentsArr.optString(i))
            }
        }
        return DeviceInfo(
            apiVersion = apiVersion,
            deviceName = root.optStringOrNull("device_name"),
            deviceId = root.optStringOrNull("device_id"),
            firmwareVersion = root.optStringOrNull("firmware_version"),
            chip = root.optStringOrNull("chip"),
            mac = root.optStringOrNull("mac"),
            ip = root.optStringOrNull("ip"),
            wifiMode = root.optStringOrNull("wifi_mode"),
            uptimeMs = root.optLongOrNull("uptime_ms"),
            components = components,
            source = DataSource.DEVICE,
        )
    }

    fun parseObject(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw Esp32Exception(AppError.Code.INVALID_JSON, cause = e)
    }

    private fun checkApiVersion(version: Int) {
        if (version == -1) throw Esp32Exception(AppError.Code.NOT_AQUARESCUE)
        if (version != SUPPORTED_API_VERSION) {
            throw Esp32Exception(AppError.Code.UNSUPPORTED_API, version.toString(), SUPPORTED_API_VERSION.toString())
        }
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
}

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) (opt(key) as? Number)?.toInt() else null

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) (opt(key) as? Number)?.toLong() else null

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

internal fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) opt(key) as? Boolean else null

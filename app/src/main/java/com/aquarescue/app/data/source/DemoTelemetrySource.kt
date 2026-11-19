package com.aquarescue.app.data.source

import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.AudioData
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DemoScenario
import com.aquarescue.app.model.DepthData
import com.aquarescue.app.model.DeviceInfo
import com.aquarescue.app.model.DeviceWifi
import com.aquarescue.app.model.GpsData
import com.aquarescue.app.model.SensorStatus
import com.aquarescue.app.model.SosData
import com.aquarescue.app.model.SosSource
import com.aquarescue.app.model.TelemetrySnapshot
import com.aquarescue.app.network.Esp32Exception
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Генератор демонстрационных данных. Все снимки помечены [DataSource.DEMO] и
 * никогда не сохраняются как данные реального устройства.
 *
 * Модель имитирует поведение реальной прошивки: под водой (глубина > 0,2 м)
 * NEO-6M теряет фиксацию, а в ответе остаются последние известные координаты.
 */
class DemoTelemetrySource : TelemetrySource {

    @Volatile
    var scenario: DemoScenario = DemoScenario.AUTO
        set(value) {
            if (field != value) scenarioStartedAt = System.currentTimeMillis()
            field = value
        }

    private val startedAt = System.currentTimeMillis()
    private var scenarioStartedAt = startedAt

    @Volatile private var sosActive = false
    @Volatile private var sosSince = 0L
    @Volatile private var muted = false

    private var lastFixLat: Double? = null
    private var lastFixLon: Double? = null
    private var lastFixAt: Long = 0L

    override suspend fun fetchStatus(): TelemetrySnapshot {
        delay(NETWORK_LATENCY_MS)
        val now = System.currentTimeMillis()
        if (scenario == DemoScenario.CONNECTION_LOST) {
            delay(1500)
            throw Esp32Exception(AppError.Code.DEMO_CONNECTION_LOST)
        }

        val t = (now - scenarioStartedAt) / 1000.0
        val sensorFault = scenario == DemoScenario.SENSOR_FAULT
        val depth = depthFor(t)

        // Дрейф по поверхности: медленное смещение + небольшое «покачивание».
        val drift = (now - startedAt) / 1000.0
        val lat = BASE_LAT + drift * 0.0000025 + sin(drift / 9.0) * 0.00002
        val lon = BASE_LON + drift * 0.0000040 + cos(drift / 11.0) * 0.00002
        val gpsFix = depth < 0.2
        if (gpsFix) {
            lastFixLat = lat
            lastFixLon = lon
            lastFixAt = now
        }
        val hasCoords = lastFixLat != null

        val pressure = SURFACE_PRESSURE_MBAR + depth * 98.1
        val sensors = mapOf(
            "ms5837" to if (sensorFault) SensorStatus.ERROR else SensorStatus.OK,
            "gps" to if (gpsFix) SensorStatus.OK else SensorStatus.NO_FIX,
            "dfplayer" to SensorStatus.OK,
            "mic" to SensorStatus.OK,
            "sos_button" to SensorStatus.OK,
        )

        return TelemetrySnapshot(
            source = DataSource.DEMO,
            apiVersion = 1,
            deviceId = "demo-vest",
            uptimeMs = now - startedAt,
            depth = DepthData(
                valid = !sensorFault,
                depthM = if (sensorFault) null else depth,
                pressureMbar = if (sensorFault) null else pressure,
                temperatureC = if (sensorFault) null else 17.8 - depth * 0.4 + sin(t / 20) * 0.1,
            ),
            gps = GpsData(
                valid = hasCoords,
                fix = gpsFix,
                lat = lastFixLat,
                lon = lastFixLon,
                satellites = if (gpsFix) 7 + (sin(t / 7) * 1.5).toInt() else 0,
                hdop = if (gpsFix) 1.1 + (sin(t / 5) + 1) * 0.2 else null,
                ageMs = if (hasCoords) now - lastFixAt else null,
            ),
            sos = SosData(
                active = sosActive,
                source = if (sosActive) SosSource.APP else SosSource.NONE,
                sinceMs = if (sosActive) now - sosSince else null,
            ),
            audio = AudioData(muted = muted, dfPlayerOk = true, buzzerOn = sosActive && !muted),
            sensors = sensors,
            wifi = DeviceWifi(mode = "demo", rssi = -55 + (sin(t / 6) * 6).toInt(), ip = null),
            deviceAlarm = null as AlarmState?,
            receivedAt = now,
        )
    }

    override suspend fun fetchInfo(): DeviceInfo {
        delay(NETWORK_LATENCY_MS)
        return DeviceInfo(
            apiVersion = 1,
            deviceName = "AquaRescue Vest",
            deviceId = "demo-vest",
            firmwareVersion = "demo",
            chip = "ESP32-WROOM-32",
            mac = null,
            ip = null,
            wifiMode = "demo",
            uptimeMs = System.currentTimeMillis() - startedAt,
            components = listOf("MS5837-30BA", "NEO-6M", "DFPlayer Mini", "MAX9814", "SOS button", "Active buzzer"),
            source = DataSource.DEMO,
        )
    }

    override suspend fun sendSos(activate: Boolean): Boolean {
        delay(NETWORK_LATENCY_MS * 2)
        if (scenario == DemoScenario.CONNECTION_LOST) {
            throw Esp32Exception(AppError.Code.DEMO_SOS_NO_RESPONSE)
        }
        sosActive = activate
        sosSince = System.currentTimeMillis()
        return true
    }

    override suspend fun setMuted(muted: Boolean): Boolean {
        delay(NETWORK_LATENCY_MS)
        if (scenario == DemoScenario.CONNECTION_LOST) {
            throw Esp32Exception(AppError.Code.DEMO_NO_RESPONSE)
        }
        this.muted = muted
        return muted
    }

    /** Глубина (м) для текущего сценария в момент t секунд от его начала. */
    private fun depthFor(t: Double): Double {
        val waves = 0.04 * (sin(t * 1.3) + 1)
        return when (scenario) {
            DemoScenario.NORMAL, DemoScenario.SENSOR_FAULT, DemoScenario.CONNECTION_LOST -> 0.03 + waves
            DemoScenario.WARNING -> 0.8 + 0.08 * sin(t / 2)
            DemoScenario.DANGER -> 2.3 + 0.15 * sin(t / 3)
            DemoScenario.AUTO -> autoCycleDepth(t % AUTO_CYCLE_SEC) + waves
        }
    }

    /** Цикл 100 с: поверхность → погружение до 2,2 м → всплытие → поверхность. */
    private fun autoCycleDepth(t: Double): Double = when {
        t < 25 -> 0.0
        t < 40 -> ease((t - 25) / 15) * 0.9
        t < 50 -> 0.9 + ease((t - 40) / 10) * 1.3
        t < 60 -> 2.2
        t < 75 -> 2.2 * (1 - ease((t - 60) / 15))
        else -> 0.0
    }

    private fun ease(x: Double): Double = (1 - cos(x.coerceIn(0.0, 1.0) * PI)) / 2

    private companion object {
        // Акватория Каспийского моря у г. Актау — демонстрационная точка.
        const val BASE_LAT = 43.640120
        const val BASE_LON = 51.120450
        const val SURFACE_PRESSURE_MBAR = 1013.25
        const val AUTO_CYCLE_SEC = 100.0
        const val NETWORK_LATENCY_MS = 120L
    }
}

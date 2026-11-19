package com.aquarescue.app.domain

import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.AppSettings
import com.aquarescue.app.model.AudioData
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DepthData
import com.aquarescue.app.model.GpsData
import com.aquarescue.app.model.SensorStatus
import com.aquarescue.app.model.SosData
import com.aquarescue.app.model.SosSource
import com.aquarescue.app.model.TelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmEvaluatorTest {

    private val evaluator = AlarmEvaluator()
    private val settings = AppSettings(warningDepthM = 0.5, dangerDepthM = 1.5, submersionLimitSec = 15)
    private val now = 100_000L

    private fun snapshot(
        depth: Double? = 0.1,
        depthValid: Boolean = true,
        sos: Boolean = false,
        sensors: Map<String, SensorStatus> = mapOf("ms5837" to SensorStatus.OK, "gps" to SensorStatus.OK),
        deviceAlarm: AlarmState? = null,
    ) = TelemetrySnapshot(
        source = DataSource.DEVICE, apiVersion = 1, deviceId = "t", uptimeMs = 1,
        depth = DepthData(depthValid, depth, 1013.0, 18.0),
        gps = GpsData(true, true, 43.0, 51.0, 7, 1.0, 0),
        sos = SosData(sos, if (sos) SosSource.BUTTON else SosSource.NONE, null),
        audio = AudioData(false, true, false),
        sensors = sensors, wifi = null, deviceAlarm = deviceAlarm, receivedAt = now,
    )

    private fun eval(s: TelemetrySnapshot?, connection: ConnectionStatus = ConnectionStatus.CONNECTED, submergedSince: Long? = null) =
        evaluator.evaluate(s, connection, settings, submergedSince, now).state

    @Test fun surfaceIsNormal() = assertEquals(AlarmState.NORMAL, eval(snapshot(depth = 0.1)))

    @Test fun shallowSubmersionIsWarning() =
        assertEquals(AlarmState.WARNING, eval(snapshot(depth = 0.8), submergedSince = now - 3_000))

    @Test fun longSubmersionIsDanger() =
        assertEquals(AlarmState.DANGER, eval(snapshot(depth = 0.8), submergedSince = now - 20_000))

    @Test fun deepIsDanger() = assertEquals(AlarmState.DANGER, eval(snapshot(depth = 1.6), submergedSince = now))

    @Test fun sosIsDanger() = assertEquals(AlarmState.DANGER, eval(snapshot(depth = 0.0, sos = true)))

    @Test fun connectionLostOverridesEverything() =
        assertEquals(AlarmState.CONNECTION_LOST, eval(snapshot(sos = true), ConnectionStatus.LOST))

    @Test fun brokenDepthSensorIsWarning() =
        assertEquals(AlarmState.WARNING, eval(snapshot(depth = null, depthValid = false)))

    @Test fun gpsWithoutFixIsNotAWarning() =
        assertEquals(AlarmState.NORMAL, eval(snapshot(sensors = mapOf("gps" to SensorStatus.NO_FIX))))

    @Test fun deviceDangerIsRespected() =
        assertEquals(AlarmState.DANGER, eval(snapshot(deviceAlarm = AlarmState.DANGER)))

    @Test fun noDataYetIsNotAnAlarm() = assertEquals(AlarmState.NORMAL, eval(null, ConnectionStatus.CONNECTING))
}

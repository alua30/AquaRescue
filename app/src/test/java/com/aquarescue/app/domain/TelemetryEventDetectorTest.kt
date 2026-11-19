package com.aquarescue.app.domain

import com.aquarescue.app.model.AudioData
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DepthData
import com.aquarescue.app.model.EventType
import com.aquarescue.app.model.GpsData
import com.aquarescue.app.model.MonitorState
import com.aquarescue.app.model.SosData
import com.aquarescue.app.model.SosSource
import com.aquarescue.app.model.TelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEventDetectorTest {

    private fun snap(depth: Double, fix: Boolean, sos: SosSource? = null) = TelemetrySnapshot(
        source = DataSource.DEVICE, apiVersion = 1, deviceId = "t", uptimeMs = 1,
        depth = DepthData(true, depth, null, null),
        gps = GpsData(true, fix, 43.0, 51.0, 7, 1.0, 0),
        sos = SosData(sos != null, sos ?: SosSource.NONE, null),
        audio = AudioData(false, true, false), sensors = emptyMap(), wifi = null, deviceAlarm = null, receivedAt = 0,
    )

    private fun state(connection: ConnectionStatus, s: TelemetrySnapshot? = null) =
        MonitorState(source = DataSource.DEVICE, connection = connection, snapshot = s)

    @Test
    fun logsFirstConnectionLossAndRestore() {
        val d = TelemetryEventDetector()
        val connected = state(ConnectionStatus.CONNECTED, snap(0.1, true))
        val first = d.detect(state(ConnectionStatus.CONNECTING), connected, 1_000)
        assertTrue(first.any { it.type == EventType.CONNECTED })

        val lost = connected.copy(connection = ConnectionStatus.LOST)
        assertEquals(listOf(EventType.CONNECTION_LOST), d.detect(connected, lost, 2_000).map { it.type })

        val restored = d.detect(lost, connected, 3_000).map { it.type }
        assertTrue(EventType.CONNECTION_RESTORED in restored)
    }

    @Test
    fun logsGpsLossWhenSubmerging() {
        val d = TelemetryEventDetector()
        val surface = state(ConnectionStatus.CONNECTED, snap(0.1, fix = true))
        d.detect(state(ConnectionStatus.CONNECTING), surface, 1_000)
        val underwater = surface.copy(snapshot = snap(0.8, fix = false))
        val events = d.detect(surface, underwater, 10_000).map { it.type }
        assertTrue(EventType.GPS_FIX_LOST in events)
        assertTrue(EventType.DEPTH_CHANGE in events)
    }

    @Test
    fun logsSosButtonPress() {
        val d = TelemetryEventDetector()
        val calm = state(ConnectionStatus.CONNECTED, snap(0.1, true))
        d.detect(state(ConnectionStatus.CONNECTING), calm, 1_000)
        val sos = calm.copy(snapshot = snap(0.1, true, SosSource.BUTTON))
        assertTrue(d.detect(calm, sos, 2_000).any { it.type == EventType.SOS_DEVICE })
    }

    @Test
    fun smallDepthChangesAreNotLogged() {
        val d = TelemetryEventDetector()
        val a = state(ConnectionStatus.CONNECTED, snap(0.10, true))
        d.detect(state(ConnectionStatus.CONNECTING), a, 1_000)
        val b = a.copy(snapshot = snap(0.30, true))
        assertTrue(d.detect(a, b, 10_000).none { it.type == EventType.DEPTH_CHANGE })
    }
}

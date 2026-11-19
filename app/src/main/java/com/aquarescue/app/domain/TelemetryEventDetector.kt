package com.aquarescue.app.domain

import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.EventType
import com.aquarescue.app.model.HistoryEvent
import com.aquarescue.app.model.MonitorState
import com.aquarescue.app.model.SensorStatus
import com.aquarescue.app.model.SosSource
import kotlin.math.abs

/**
 * Превращает изменения состояния мониторинга в события истории.
 * Содержит фильтры, чтобы журнал не засорялся: глубина — шаг 0,5 м,
 * перемещение GPS — от 25 м и не чаще раза в 30 с.
 * События не содержат готового текста — только тип и параметры.
 */
class TelemetryEventDetector {

    private var connectedOnce = false
    private var lastLoggedAlarm = AlarmState.NORMAL
    private var lastLoggedDepth: Double? = null
    private var lastDepthLogAt = 0L
    private var lastGpsLat: Double? = null
    private var lastGpsLon: Double? = null
    private var lastGpsLogAt = 0L
    private var faultySensors = emptySet<String>()

    /** SOS был отменён из приложения — снятие SOS на устройстве уже записано. */
    var suppressNextSosOff = false

    fun reset() {
        connectedOnce = false
        lastLoggedAlarm = AlarmState.NORMAL
        lastLoggedDepth = null
        lastDepthLogAt = 0L
        lastGpsLat = null
        lastGpsLon = null
        lastGpsLogAt = 0L
        faultySensors = emptySet()
        suppressNextSosOff = false
    }

    fun detect(old: MonitorState, new: MonitorState, now: Long): List<HistoryEvent> {
        val events = mutableListOf<HistoryEvent>()
        fun event(type: EventType) = HistoryEvent(id = 0, timestamp = now, type = type, source = new.source)
            .also { events += it }
        fun replaceLast(e: HistoryEvent) { events[events.lastIndex] = e }

        // --- Связь ---
        if (new.connection == ConnectionStatus.CONNECTED && old.connection != ConnectionStatus.CONNECTED &&
            old.connection != ConnectionStatus.RECONNECTING
        ) {
            if (old.connection == ConnectionStatus.LOST && connectedOnce) {
                event(EventType.CONNECTION_RESTORED)
            } else if (!connectedOnce) {
                replaceLast(event(EventType.CONNECTED).copy(text = new.endpointLabel))
            }
            connectedOnce = true
        }
        if (new.connection == ConnectionStatus.LOST && old.connection != ConnectionStatus.LOST) {
            replaceLast(event(EventType.CONNECTION_LOST).copy(flag = !connectedOnce, error = new.lastError))
        }

        val snap = new.snapshot ?: return events
        val prevSnap = old.snapshot
        val depthNow = snap.depth.depthM
        val fix = new.lastKnownFix

        // --- Тревога ---
        val alarm = new.alarm.state
        if (alarm != AlarmState.CONNECTION_LOST && alarm != lastLoggedAlarm) {
            val type = when (alarm) {
                AlarmState.DANGER -> EventType.DANGER
                AlarmState.WARNING -> EventType.WARNING
                else -> EventType.NORMAL_RESTORED
            }
            replaceLast(event(type).copy(depthM = depthNow, reasons = new.alarm.reasons))
            lastLoggedAlarm = alarm
        }

        // --- SOS ---
        val sosWasActive = prevSnap?.sos?.active == true
        if (snap.sos.active && !sosWasActive && snap.sos.source == SosSource.BUTTON) {
            replaceLast(event(EventType.SOS_DEVICE).copy(depthM = depthNow, lat = fix?.lat, lon = fix?.lon))
        }
        if (!snap.sos.active && sosWasActive) {
            if (suppressNextSosOff) {
                suppressNextSosOff = false
            } else {
                event(EventType.SOS_CLEARED)
            }
        }

        // --- Датчики ---
        val faulty = snap.sensors.filterValues { it == SensorStatus.ERROR }.keys
        (faulty - faultySensors).forEach { key -> replaceLast(event(EventType.SENSOR_FAULT).copy(text = key)) }
        faultySensors = faulty

        // --- Глубина ---
        if (snap.depth.valid && depthNow != null) {
            val logged = lastLoggedDepth
            if (logged == null) {
                lastLoggedDepth = depthNow
            } else if (abs(depthNow - logged) >= DEPTH_STEP_M && now - lastDepthLogAt >= DEPTH_MIN_INTERVAL_MS) {
                replaceLast(
                    event(EventType.DEPTH_CHANGE).copy(
                        depthM = depthNow,
                        value = abs(depthNow - logged),
                        flag = depthNow < logged, // всплытие
                    )
                )
                lastLoggedDepth = depthNow
                lastDepthLogAt = now
            }
        }

        // --- GPS ---
        val gps = snap.gps
        val hadFix = prevSnap?.gps?.fix == true
        if (gps.fix && gps.lat != null && gps.lon != null) {
            if (!hadFix) {
                replaceLast(event(EventType.GPS_FIX_ACQUIRED).copy(lat = gps.lat, lon = gps.lon, count = gps.satellites))
                markGps(gps.lat, gps.lon, now)
            } else {
                val pLat = lastGpsLat
                val pLon = lastGpsLon
                val moved = if (pLat != null && pLon != null) GeoMath.distanceMeters(pLat, pLon, gps.lat, gps.lon) else 0.0
                if (moved >= GPS_MIN_DISTANCE_M && now - lastGpsLogAt >= GPS_MIN_INTERVAL_MS) {
                    replaceLast(event(EventType.GPS_UPDATE).copy(lat = gps.lat, lon = gps.lon, value = moved))
                    markGps(gps.lat, gps.lon, now)
                }
            }
        } else if (hadFix) {
            replaceLast(event(EventType.GPS_FIX_LOST).copy(depthM = depthNow, lat = fix?.lat, lon = fix?.lon))
        }
        return events
    }

    private fun markGps(lat: Double, lon: Double, now: Long) {
        lastGpsLat = lat
        lastGpsLon = lon
        lastGpsLogAt = now
    }

    companion object {
        const val DEPTH_STEP_M = 0.5
        const val DEPTH_MIN_INTERVAL_MS = 3_000L
        const val GPS_MIN_DISTANCE_M = 25.0
        const val GPS_MIN_INTERVAL_MS = 30_000L
    }
}

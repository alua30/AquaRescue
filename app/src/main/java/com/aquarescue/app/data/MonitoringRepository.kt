package com.aquarescue.app.data

import android.util.Log
import com.aquarescue.app.data.alarm.PhoneAlarmPlayer
import com.aquarescue.app.data.source.DemoTelemetrySource
import com.aquarescue.app.data.source.DeviceTelemetrySource
import com.aquarescue.app.data.source.TelemetrySource
import com.aquarescue.app.domain.AlarmEvaluator
import com.aquarescue.app.domain.GeoMath
import com.aquarescue.app.domain.TelemetryEventDetector
import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.AppSettings
import com.aquarescue.app.model.CommandResult
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DeviceInfo
import com.aquarescue.app.model.EventType
import com.aquarescue.app.model.GeoFix
import com.aquarescue.app.model.HistoryEvent
import com.aquarescue.app.model.MonitorState
import com.aquarescue.app.model.TelemetrySnapshot
import com.aquarescue.app.network.Esp32ApiClient
import com.aquarescue.app.network.Esp32Endpoint
import com.aquarescue.app.network.Esp32Exception
import com.aquarescue.app.network.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Сердце приложения: опрашивает активный источник (демо или ESP32), обрабатывает
 * потерю связи с автоматическим переподключением, вычисляет состояние тревоги,
 * пишет события в историю и выполняет команды (SOS, звук).
 *
 * При смене источника (демо ↔ устройство) или адреса состояние полностью сбрасывается,
 * чтобы демо-значения никогда не смешивались с реальными.
 */
class MonitoringRepository(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val apiClient: Esp32ApiClient,
    private val alarmPlayer: PhoneAlarmPlayer,
    private val alarmEvaluator: AlarmEvaluator = AlarmEvaluator(),
) {
    private val demoSource = DemoTelemetrySource()
    private val eventDetector = TelemetryEventDetector()
    private val stateMutex = Mutex()

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    @Volatile private var activeSource: TelemetrySource? = null
    @Volatile private var pollWakeUp: Job? = null
    private var started = false
    private var lastFixPersistAt = 0L

    private data class SessionKey(
        val demo: Boolean,
        val host: String,
        val port: Int,
        val enabled: Boolean,
        val pollIntervalMs: Long,
    )

    fun start() {
        if (started) return
        started = true
        scope.launch {
            settingsRepository.settingsFlow.collect { demoSource.scenario = it.demoScenario }
        }
        scope.launch {
            settingsRepository.settingsFlow
                .map { SessionKey(it.demoMode, it.host, it.port, it.connectionEnabled, it.pollIntervalMs) }
                .distinctUntilChanged()
                .collectLatest { key -> runSession(key) }
        }
    }

    // ------------------------------------------------------------------ Опрос

    private suspend fun runSession(key: SessionKey) {
        val source: TelemetrySource = if (key.demo) {
            demoSource
        } else {
            DeviceTelemetrySource(apiClient, Esp32Endpoint(key.host, key.port))
        }
        activeSource = source
        val dataSource = if (key.demo) DataSource.DEMO else DataSource.DEVICE
        val persistedFix = if (key.demo) null else settingsRepository.loadLastDeviceFix()

        stateMutex.withLock {
            eventDetector.reset()
            _state.value = MonitorState(
                source = dataSource,
                endpointLabel = if (key.demo) null else "${key.host}:${key.port}",
                connection = if (key.enabled) ConnectionStatus.CONNECTING else ConnectionStatus.DISABLED,
                sessionStartedAt = System.currentTimeMillis(),
                lastKnownFix = persistedFix,
            )
        }
        if (!key.enabled) return

        var failures = 0
        var infoLoaded = false
        while (true) {
            try {
                val snapshot = source.fetchStatus()
                failures = 0
                onSnapshot(snapshot)
                if (!infoLoaded) {
                    infoLoaded = true
                    scope.launch { loadDeviceInfo(source) }
                }
                sleep(key.pollIntervalMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                val retryDelay = backoffMs(failures)
                onFailure(e.toAppError(), failures, retryDelay)
                sleep(retryDelay)
            }
        }
    }

    /** Пауза между опросами, которую можно прервать (например, после отправки SOS). */
    private suspend fun sleep(ms: Long) {
        val job = scope.launch { delay(ms) }
        pollWakeUp = job
        job.join()
    }

    private fun requestImmediatePoll() {
        pollWakeUp?.cancel()
    }

    private fun backoffMs(failures: Int): Long = when (failures) {
        1 -> 1_000L
        2 -> 2_000L
        3 -> 4_000L
        4 -> 8_000L
        else -> 10_000L
    }

    private suspend fun loadDeviceInfo(source: TelemetrySource) {
        try {
            val info: DeviceInfo = source.fetchInfo()
            stateMutex.withLock {
                if (activeSource === source) _state.value = _state.value.copy(deviceInfo = info)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "GET /api/info failed: ${e.toAppError()}")
        }
    }

    private suspend fun onSnapshot(snapshot: TelemetrySnapshot) = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val settings = settingsRepository.settings.value
        val old = _state.value

        val depth = snapshot.depth.depthM?.takeIf { snapshot.depth.valid }
        val submergedSince = if (depth != null && depth >= settings.warningDepthM) {
            old.submergedSince ?: now
        } else {
            null
        }

        // Последние известные координаты: обновляются только при актуальной фиксации,
        // либо восстанавливаются из ответа устройства, если приложение их ещё не знало.
        var lastFix = old.lastKnownFix
        var track = old.track
        val gps = snapshot.gps
        if (gps.valid && gps.lat != null && gps.lon != null) {
            val fixTime = now - (gps.ageMs ?: 0L)
            if (gps.fix) {
                val fix = GeoFix(gps.lat, gps.lon, fixTime, gps.satellites, gps.hdop, snapshot.source)
                lastFix = fix
                val prev = track.lastOrNull()
                if (prev == null || GeoMath.distanceMeters(prev.lat, prev.lon, fix.lat, fix.lon) >= TRACK_MIN_STEP_M) {
                    track = (track + fix).takeLast(TRACK_MAX_POINTS)
                }
            } else if (lastFix == null || lastFix.source != snapshot.source ||
                GeoMath.distanceMeters(lastFix.lat, lastFix.lon, gps.lat, gps.lon) > 1.0
            ) {
                lastFix = GeoFix(gps.lat, gps.lon, fixTime, gps.satellites, gps.hdop, snapshot.source)
            }
        }

        val connection = ConnectionStatus.CONNECTED
        val newState = old.copy(
            connection = connection,
            snapshot = snapshot,
            lastUpdateAt = now,
            lastError = null,
            consecutiveFailures = 0,
            nextRetryAt = null,
            alarm = alarmEvaluator.evaluate(snapshot, connection, settings, submergedSince, now),
            lastKnownFix = lastFix,
            track = track,
            maxDepthM = listOfNotNull(old.maxDepthM, depth).maxOrNull(),
            submergedSince = submergedSince,
        )
        commit(old, newState, now, settings)

        if (lastFix != null && lastFix.source == DataSource.DEVICE && lastFix != old.lastKnownFix &&
            now - lastFixPersistAt > FIX_PERSIST_INTERVAL_MS
        ) {
            lastFixPersistAt = now
            val toSave = lastFix
            scope.launch { settingsRepository.saveLastDeviceFix(toSave) }
        }
    }

    private suspend fun onFailure(error: AppError, failures: Int, retryDelayMs: Long) = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val settings = settingsRepository.settings.value
        val old = _state.value
        val reference = old.lastUpdateAt ?: old.sessionStartedAt
        val lost = now - reference >= AlarmEvaluator.LOST_TIMEOUT_MS
        val connection = when {
            lost -> ConnectionStatus.LOST
            old.lastUpdateAt != null -> ConnectionStatus.RECONNECTING
            else -> ConnectionStatus.CONNECTING
        }
        val newState = old.copy(
            connection = connection,
            lastError = error,
            consecutiveFailures = failures,
            nextRetryAt = now + retryDelayMs,
            alarm = if (lost) {
                alarmEvaluator.evaluate(old.snapshot, connection, settings, old.submergedSince, now)
            } else {
                old.alarm
            },
        )
        commit(old, newState, now, settings)
    }

    /** Публикует новое состояние, пишет события и включает звуковое оповещение. */
    private fun commit(old: MonitorState, new: MonitorState, now: Long, settings: AppSettings) {
        val events = eventDetector.detect(old, new, now)
        _state.value = new
        historyRepository.add(events)

        val wasState = old.alarm.state
        val isState = new.alarm.state
        if (isState != wasState) {
            if (isState == AlarmState.DANGER && settings.phoneAlarmOnDanger) {
                alarmPlayer.playDanger()
            }
            // Сигнал о потере связи — только если связь действительно была.
            if (isState == AlarmState.CONNECTION_LOST && old.lastUpdateAt != null &&
                settings.phoneAlarmOnConnectionLost
            ) {
                alarmPlayer.playConnectionLost()
            }
        }
    }

    // --------------------------------------------------------------- Команды

    /**
     * Отправляет SOS (или его отмену). Успех возвращается ТОЛЬКО если устройство
     * подтвердило команду. В демо-режиме подтверждение явно помечено как имитация.
     */
    suspend fun sendSos(activate: Boolean): CommandResult {
        val st = _state.value
        val source = activeSource
        val now = System.currentTimeMillis()

        if (st.connection == ConnectionStatus.DISABLED || source == null) {
            val error = AppError(AppError.Code.CONNECTION_DISABLED)
            if (activate) logSos(EventType.SOS_FAILED, st, now, flag = true, error = error)
            return CommandResult.NotConnected(error)
        }
        return try {
            source.sendSos(activate)
            if (activate) {
                logSos(EventType.SOS_SENT, st, now)
            } else {
                eventDetector.suppressNextSosOff = true
                logSos(EventType.SOS_CANCELLED, st, now)
            }
            requestImmediatePoll()
            CommandResult.Confirmed(demo = st.source == DataSource.DEMO, value = activate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toAppError()
            logSos(EventType.SOS_FAILED, st, now, flag = activate, error = error)
            CommandResult.Failed(error)
        }
    }

    suspend fun setDeviceMuted(muted: Boolean): CommandResult {
        val st = _state.value
        val source = activeSource
        if (st.connection == ConnectionStatus.DISABLED || source == null || !st.hasData) {
            return CommandResult.NotConnected(AppError(AppError.Code.NO_CONNECTION))
        }
        return try {
            val confirmed = source.setMuted(muted)
            requestImmediatePoll()
            CommandResult.Confirmed(demo = st.source == DataSource.DEMO, value = confirmed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CommandResult.Failed(e.toAppError())
        }
    }

    /**
     * Разовая проверка связи с ESP32 по указанному адресу (не влияет на текущий сеанс).
     * При ошибке исключение — [Esp32Exception] с кодом ошибки.
     */
    suspend fun checkConnection(host: String, port: Int): Result<DeviceInfo> = try {
        Result.success(apiClient.getInfo(Esp32Endpoint(host, port)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(Esp32Exception(e.toAppError(), e))
    }

    /** Повторить подключение немедленно, не дожидаясь паузы. */
    fun retryNow() = requestImmediatePoll()

    fun testPhoneAlarm() = alarmPlayer.playTest()

    private fun logSos(
        type: EventType,
        st: MonitorState,
        now: Long,
        flag: Boolean = false,
        error: AppError? = null,
    ) {
        historyRepository.add(
            HistoryEvent(
                id = 0, timestamp = now, type = type, source = st.source,
                depthM = st.snapshot?.depth?.depthM,
                lat = st.lastKnownFix?.lat, lon = st.lastKnownFix?.lon,
                flag = flag, error = error,
            )
        )
    }

    private companion object {
        const val TAG = "MonitoringRepository"
        const val TRACK_MAX_POINTS = 200
        const val TRACK_MIN_STEP_M = 2.0
        const val FIX_PERSIST_INTERVAL_MS = 10_000L
    }
}

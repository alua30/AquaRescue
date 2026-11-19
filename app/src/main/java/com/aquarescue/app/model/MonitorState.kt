package com.aquarescue.app.model

/** Полное состояние мониторинга жилета, общее для всех экранов. */
data class MonitorState(
    val source: DataSource = DataSource.DEMO,
    /** Адрес ESP32 (null в демо-режиме). */
    val endpointLabel: String? = null,
    val connection: ConnectionStatus = ConnectionStatus.CONNECTING,
    /** Последний успешно полученный снимок текущего источника. */
    val snapshot: TelemetrySnapshot? = null,
    /** Время последнего успешного обновления данных (часы телефона). */
    val lastUpdateAt: Long? = null,
    val sessionStartedAt: Long = System.currentTimeMillis(),
    val lastError: AppError? = null,
    val consecutiveFailures: Int = 0,
    val nextRetryAt: Long? = null,
    val alarm: AlarmEvaluation = AlarmEvaluation(AlarmState.NORMAL, emptyList()),
    /** Последние известные координаты (могут быть устаревшими!). */
    val lastKnownFix: GeoFix? = null,
    /** Последние точки траектории (только актуальные фиксации). */
    val track: List<GeoFix> = emptyList(),
    val maxDepthM: Double? = null,
    /** С какого момента устройство глубже порога предупреждения. */
    val submergedSince: Long? = null,
    val deviceInfo: DeviceInfo? = null,
) {
    val hasData: Boolean get() = snapshot != null
    val isOnline: Boolean
        get() = connection == ConnectionStatus.CONNECTED || connection == ConnectionStatus.RECONNECTING
}

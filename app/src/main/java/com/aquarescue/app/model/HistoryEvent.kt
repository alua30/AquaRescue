package com.aquarescue.app.model

/**
 * Событие истории. Хранится в структурированном виде (тип + параметры),
 * а заголовок и описание формируются на языке интерфейса при отображении.
 *
 * Значение универсальных полей зависит от [type]:
 * - [value]: изменение глубины (DEPTH_CHANGE) или смещение в метрах (GPS_UPDATE);
 * - [count]: число спутников (GPS_FIX_ACQUIRED);
 * - [flag]: DEPTH_CHANGE — всплытие; CONNECTION_LOST — связи ещё не было;
 *           SOS_FAILED — это была попытка активации (а не отмены);
 * - [text]: ключ датчика (SENSOR_FAULT) или адрес устройства (CONNECTED).
 */
data class HistoryEvent(
    val id: Long,
    val timestamp: Long,
    val type: EventType,
    val source: DataSource,
    val depthM: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val value: Double? = null,
    val count: Int? = null,
    val flag: Boolean = false,
    val text: String? = null,
    val error: AppError? = null,
    val reasons: List<AlarmReason> = emptyList(),
)

enum class EventType(val category: EventCategory) {
    DEPTH_CHANGE(EventCategory.DEPTH),
    GPS_FIX_ACQUIRED(EventCategory.GPS),
    GPS_FIX_LOST(EventCategory.GPS),
    GPS_UPDATE(EventCategory.GPS),
    SOS_DEVICE(EventCategory.SOS),
    SOS_SENT(EventCategory.SOS),
    SOS_FAILED(EventCategory.SOS),
    /** SOS отменён из приложения, устройство подтвердило. */
    SOS_CANCELLED(EventCategory.SOS),
    /** SOS снят на самом жилете. */
    SOS_CLEARED(EventCategory.SOS),
    WARNING(EventCategory.ALARM),
    DANGER(EventCategory.ALARM),
    NORMAL_RESTORED(EventCategory.ALARM),
    SENSOR_FAULT(EventCategory.ALARM),
    CONNECTION_LOST(EventCategory.CONNECTION),
    CONNECTION_RESTORED(EventCategory.CONNECTION),
    CONNECTED(EventCategory.CONNECTION),
}

enum class EventCategory { ALARM, SOS, DEPTH, GPS, CONNECTION }

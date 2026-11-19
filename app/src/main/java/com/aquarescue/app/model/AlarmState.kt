package com.aquarescue.app.model

enum class AlarmState(val severity: Int) {
    NORMAL(0),
    WARNING(1),
    DANGER(3),
    CONNECTION_LOST(2);

    companion object {
        fun parseDevice(value: String?): AlarmState? = when (value?.uppercase()) {
            "NORMAL" -> NORMAL
            "WARNING" -> WARNING
            "DANGER" -> DANGER
            else -> null
        }
    }
}

/**
 * Причина тревоги без готового текста. Числовые параметры:
 * [a] — глубина или число секунд, [b] — порог.
 */
data class AlarmReason(
    val kind: Kind,
    val a: Double? = null,
    val b: Double? = null,
) {
    enum class Kind {
        NO_DATA,
        SOS_BUTTON,
        SOS_APP,
        SOS_ACTIVE,
        DEPTH_DANGER,
        SUBMERGED_TOO_LONG,
        DEPTH_WARNING,
        DEPTH_SENSOR_UNAVAILABLE,
        GPS_MODULE_ERROR,
        DFPLAYER_ERROR,
        SOS_BUTTON_FAULT,
        DEVICE_DANGER,
        DEVICE_WARNING,
    }
}

/** Результат оценки тревоги: состояние и причины. */
data class AlarmEvaluation(
    val state: AlarmState,
    val reasons: List<AlarmReason>,
)

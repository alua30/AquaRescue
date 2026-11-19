package com.aquarescue.app.model

data class AppSettings(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    /** Демонстрационный режим: данные генерируются приложением, ESP32 не опрашивается. */
    val demoMode: Boolean = true,
    /** Пользователь разрешил подключение к ESP32 (кнопка «Подключиться/Отключиться»). */
    val connectionEnabled: Boolean = true,
    val pollIntervalMs: Long = 1000,
    /** Звуковое оповещение на телефоне при опасности. */
    val phoneAlarmOnDanger: Boolean = true,
    /** Звуковое оповещение на телефоне при потере связи. */
    val phoneAlarmOnConnectionLost: Boolean = true,
    val warningDepthM: Double = 0.5,
    val dangerDepthM: Double = 1.5,
    /** Сколько секунд погружения глубже порога предупреждения считать опасностью. */
    val submersionLimitSec: Int = 15,
    val demoScenario: DemoScenario = DemoScenario.AUTO,
) {
    companion object {
        const val DEFAULT_HOST = "192.168.4.1"
        const val DEFAULT_PORT = 80
    }
}

/** Сценарии демо-режима. Названия и описания — в strings.xml (scenario_*). */
enum class DemoScenario {
    AUTO,
    NORMAL,
    WARNING,
    DANGER,
    SENSOR_FAULT,
    CONNECTION_LOST,
}

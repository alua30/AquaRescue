package com.aquarescue.app.model

/** Результат команды, отправленной на устройство (SOS, звук). */
sealed interface CommandResult {
    /**
     * Устройство подтвердило выполнение. [demo] — подтверждение сымитировано в демо-режиме.
     * [value] — подтверждённое значение (для звука: true = звук выключен).
     */
    data class Confirmed(val demo: Boolean, val value: Boolean? = null) : CommandResult

    /** Команда не подтверждена (ошибка сети, ответ с ошибкой, неверное подтверждение). */
    data class Failed(val error: AppError) : CommandResult

    /** Нет связи с устройством — команда не отправлялась. */
    data class NotConnected(val error: AppError) : CommandResult
}

package com.aquarescue.app.model

/**
 * Ошибка в структурированном виде (без готового текста).
 * Текст на языке интерфейса собирается в UI (ui/text/TextResolvers.kt),
 * поэтому сообщения меняются вместе с языком, в том числе в сохранённой истории.
 */
data class AppError(
    val code: Code,
    val arg: String? = null,
    val arg2: String? = null,
) {
    enum class Code {
        TIMEOUT,
        CONNECTION_REFUSED,
        NO_ROUTE,
        UNKNOWN_HOST,
        NETWORK,
        UNKNOWN,
        INVALID_ADDRESS,
        INVALID_JSON,
        NOT_AQUARESCUE,
        MISSING_BLOCK,
        UNSUPPORTED_API,
        HTTP_ERROR,
        SOS_REJECTED,
        SOS_WRONG_REQUEST_ID,
        SOS_STATE_UNCHANGED,
        SOUND_NOT_CONFIRMED,
        DEMO_CONNECTION_LOST,
        DEMO_SOS_NO_RESPONSE,
        DEMO_NO_RESPONSE,
        CONNECTION_DISABLED,
        NO_CONNECTION,
    }
}

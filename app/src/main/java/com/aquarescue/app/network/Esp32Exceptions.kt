package com.aquarescue.app.network

import com.aquarescue.app.model.AppError
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Ошибка обмена с ESP32. Текст для пользователя формируется в UI по [error]. */
class Esp32Exception(
    val error: AppError,
    cause: Throwable? = null,
) : IOException(error.code.name, cause) {
    constructor(code: AppError.Code, arg: String? = null, arg2: String? = null, cause: Throwable? = null) :
        this(AppError(code, arg, arg2), cause)
}

fun Throwable.toAppError(): AppError = when (this) {
    is Esp32Exception -> error
    is SocketTimeoutException -> AppError(AppError.Code.TIMEOUT)
    is ConnectException -> AppError(AppError.Code.CONNECTION_REFUSED)
    is NoRouteToHostException -> AppError(AppError.Code.NO_ROUTE)
    is UnknownHostException -> AppError(AppError.Code.UNKNOWN_HOST)
    is IOException -> AppError(AppError.Code.NETWORK, message ?: javaClass.simpleName)
    else -> AppError(AppError.Code.UNKNOWN, message ?: javaClass.simpleName)
}

package com.aquarescue.app.network

import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.DeviceInfo
import com.aquarescue.app.model.TelemetrySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Адрес ESP32 в локальной сети. */
data class Esp32Endpoint(val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"
}

/** Подтверждение команды SOS от ESP32. */
data class SosAck(val ok: Boolean, val requestId: String?, val sosActive: Boolean?)

/** Подтверждение команды звука от ESP32. */
data class SoundAck(val ok: Boolean, val muted: Boolean?)

/**
 * HTTP-клиент ESP32 (контракт: docs/ESP32_API.md).
 * Используется стандартный HttpURLConnection — сторонние HTTP-библиотеки для
 * четырёх простых эндпоинтов не нужны.
 */
class Esp32ApiClient(
    private val networkMonitor: NetworkMonitor,
) {

    suspend fun getStatus(endpoint: Esp32Endpoint): TelemetrySnapshot {
        val body = request(endpoint, "GET", "/api/status")
        return Esp32JsonParser.parseStatus(body, System.currentTimeMillis())
    }

    suspend fun getInfo(endpoint: Esp32Endpoint): DeviceInfo {
        val body = request(endpoint, "GET", "/api/info")
        return Esp32JsonParser.parseInfo(body)
    }

    /** Возвращает отправленный request_id и ответ устройства. */
    suspend fun sendSos(endpoint: Esp32Endpoint, activate: Boolean): Pair<String, SosAck> {
        val requestId = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("action", if (activate) "activate" else "cancel")
            .put("request_id", requestId)
            .put("source", "app")
        val body = request(endpoint, "POST", "/api/sos", payload.toString())
        val json = Esp32JsonParser.parseObject(body)
        return requestId to SosAck(
            ok = json.optBoolean("ok", false),
            requestId = json.optStringOrNull("request_id"),
            sosActive = json.optBooleanOrNull("sos_active"),
        )
    }

    suspend fun setMuted(endpoint: Esp32Endpoint, muted: Boolean): SoundAck {
        val payload = JSONObject().put("muted", muted)
        val body = request(endpoint, "POST", "/api/sound", payload.toString())
        val json = Esp32JsonParser.parseObject(body)
        return SoundAck(ok = json.optBoolean("ok", false), muted = json.optBooleanOrNull("muted"))
    }

    private suspend fun request(
        endpoint: Esp32Endpoint,
        method: String,
        path: String,
        jsonBody: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val url = try {
            URL(endpoint.baseUrl + path)
        } catch (e: Exception) {
            throw Esp32Exception(AppError.Code.INVALID_ADDRESS, endpoint.baseUrl, cause = e)
        }
        // Привязываем запрос к Wi-Fi, если телефон подключён к Wi-Fi (см. NetworkMonitor).
        val wifiNetwork = networkMonitor.wifiState.value.network
        val connection = (wifiNetwork?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Connection", "close")
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val error = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                throw Esp32Exception(AppError.Code.HTTP_ERROR, code.toString(), error)
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 2500
        const val READ_TIMEOUT_MS = 3000
    }
}

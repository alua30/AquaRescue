package com.aquarescue.app.data.source

import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.DeviceInfo
import com.aquarescue.app.model.TelemetrySnapshot
import com.aquarescue.app.network.Esp32ApiClient
import com.aquarescue.app.network.Esp32Endpoint
import com.aquarescue.app.network.Esp32Exception

/** Реальный ESP32 по HTTP (контракт docs/ESP32_API.md). */
class DeviceTelemetrySource(
    private val api: Esp32ApiClient,
    private val endpoint: Esp32Endpoint,
) : TelemetrySource {

    override suspend fun fetchStatus(): TelemetrySnapshot = api.getStatus(endpoint)

    override suspend fun fetchInfo(): DeviceInfo = api.getInfo(endpoint)

    override suspend fun sendSos(activate: Boolean): Boolean {
        val (requestId, ack) = api.sendSos(endpoint, activate)
        if (!ack.ok) throw Esp32Exception(AppError.Code.SOS_REJECTED)
        if (ack.requestId != requestId) {
            throw Esp32Exception(AppError.Code.SOS_WRONG_REQUEST_ID)
        }
        if (ack.sosActive != activate) {
            throw Esp32Exception(AppError.Code.SOS_STATE_UNCHANGED)
        }
        return true
    }

    override suspend fun setMuted(muted: Boolean): Boolean {
        val ack = api.setMuted(endpoint, muted)
        if (!ack.ok || ack.muted == null) throw Esp32Exception(AppError.Code.SOUND_NOT_CONFIRMED)
        return ack.muted
    }
}

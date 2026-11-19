package com.aquarescue.app.model

/** Ответ GET /api/info. */
data class DeviceInfo(
    val apiVersion: Int,
    val deviceName: String?,
    val deviceId: String?,
    val firmwareVersion: String?,
    val chip: String?,
    val mac: String?,
    val ip: String?,
    val wifiMode: String?,
    val uptimeMs: Long?,
    val components: List<String>,
    val source: DataSource,
)

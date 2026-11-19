package com.aquarescue.app.data

import android.content.Context
import android.util.Log
import com.aquarescue.app.model.AlarmReason
import com.aquarescue.app.model.AppError
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.EventType
import com.aquarescue.app.model.HistoryEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Журнал событий. Хранится локально в JSON-файле (files/history.json),
 * последние [MAX_EVENTS] событий, новые — первыми.
 */
class HistoryRepository(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "history.json")
    private val writeMutex = Mutex()
    private val idCounter = AtomicLong(System.currentTimeMillis() * 1000)

    private val _events = MutableStateFlow<List<HistoryEvent>>(emptyList())
    val events: StateFlow<List<HistoryEvent>> = _events.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val stored = readFile()
            // События, добавленные до окончания загрузки, сохраняем поверх загруженных.
            _events.update { current -> (current + stored).take(MAX_EVENTS) }
            _loaded.value = true
        }
    }

    fun add(events: List<HistoryEvent>) {
        if (events.isEmpty()) return
        val withIds = events.map { it.copy(id = idCounter.incrementAndGet()) }
        _events.update { current -> (withIds.sortedByDescending { it.timestamp } + current).take(MAX_EVENTS) }
        persist()
    }

    fun add(event: HistoryEvent) = add(listOf(event))

    fun clear() {
        _events.value = emptyList()
        persist()
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                val snapshot = _events.value
                try {
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(toJson(snapshot).toString())
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save history", e)
                }
            }
        }
    }

    private fun readFile(): List<HistoryEvent> = try {
        if (!file.exists()) emptyList() else fromJson(JSONArray(file.readText()))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read history, file will be overwritten", e)
        emptyList()
    }

    private fun toJson(events: List<HistoryEvent>): JSONArray = JSONArray().apply {
        events.forEach { e ->
            put(
                JSONObject()
                    .put("id", e.id)
                    .put("ts", e.timestamp)
                    .put("type", e.type.name)
                    .put("source", e.source.name)
                    .putOpt("depth", e.depthM)
                    .putOpt("lat", e.lat)
                    .putOpt("lon", e.lon)
                    .putOpt("value", e.value)
                    .putOpt("count", e.count)
                    .put("flag", e.flag)
                    .putOpt("text", e.text)
                    .putOpt("error", e.error?.let { err ->
                        JSONObject().put("code", err.code.name).putOpt("arg", err.arg).putOpt("arg2", err.arg2)
                    })
                    .put("reasons", JSONArray().apply {
                        e.reasons.forEach { r ->
                            put(JSONObject().put("kind", r.kind.name).putOpt("a", r.a).putOpt("b", r.b))
                        }
                    })
            )
        }
    }

    /** Чтение журнала. Неизвестные типы (например, из будущих версий) пропускаются. */
    private fun fromJson(array: JSONArray): List<HistoryEvent> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val type = EventType.entries.firstOrNull { it.name == o.optString("type") } ?: continue
            val source = DataSource.entries.firstOrNull { it.name == o.optString("source") } ?: continue
            add(
                HistoryEvent(
                    id = o.optLong("id"),
                    timestamp = o.optLong("ts"),
                    type = type,
                    source = source,
                    depthM = o.optDoubleOrNull("depth"),
                    lat = o.optDoubleOrNull("lat"),
                    lon = o.optDoubleOrNull("lon"),
                    value = o.optDoubleOrNull("value"),
                    count = if (o.has("count")) o.optInt("count") else null,
                    flag = o.optBoolean("flag", false),
                    text = if (o.has("text") && !o.isNull("text")) o.optString("text") else null,
                    error = o.optJSONObject("error")?.let { err ->
                        AppError.Code.entries.firstOrNull { it.name == err.optString("code") }?.let { code ->
                            AppError(
                                code,
                                if (err.has("arg")) err.optString("arg") else null,
                                if (err.has("arg2")) err.optString("arg2") else null,
                            )
                        }
                    },
                    reasons = buildList {
                        val arr = o.optJSONArray("reasons") ?: return@buildList
                        for (j in 0 until arr.length()) {
                            val r = arr.optJSONObject(j) ?: continue
                            val kind = AlarmReason.Kind.entries.firstOrNull { it.name == r.optString("kind") } ?: continue
                            add(AlarmReason(kind, r.optDoubleOrNull("a"), r.optDoubleOrNull("b")))
                        }
                    },
                )
            )
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

    private companion object {
        const val TAG = "HistoryRepository"
        const val MAX_EVENTS = 1000
    }
}

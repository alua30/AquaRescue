package com.aquarescue.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aquarescue.app.model.AppSettings
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.DemoScenario
import com.aquarescue.app.model.GeoFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aquarescue_settings")

/** Настройки приложения и последняя известная GPS-позиция реального устройства (DataStore). */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val dataStore = context.applicationContext.dataStore

    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Поток настроек; первое значение — уже загруженные с диска настройки. */
    val settingsFlow: Flow<AppSettings> = safeData.map { it.toSettings() }

    val settings: StateFlow<AppSettings> =
        settingsFlow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.HOST] = updated.host
            prefs[Keys.PORT] = updated.port
            prefs[Keys.DEMO] = updated.demoMode
            prefs[Keys.CONNECTION_ENABLED] = updated.connectionEnabled
            prefs[Keys.POLL_INTERVAL] = updated.pollIntervalMs
            prefs[Keys.ALARM_DANGER] = updated.phoneAlarmOnDanger
            prefs[Keys.ALARM_LOST] = updated.phoneAlarmOnConnectionLost
            prefs[Keys.WARNING_DEPTH] = updated.warningDepthM
            prefs[Keys.DANGER_DEPTH] = updated.dangerDepthM
            prefs[Keys.SUBMERSION_LIMIT] = updated.submersionLimitSec
            prefs[Keys.DEMO_SCENARIO] = updated.demoScenario.name
        }
    }

    /** Последние координаты, полученные от реального ESP32 (демо-данные не сохраняются). */
    suspend fun loadLastDeviceFix(): GeoFix? {
        val prefs = safeData.first()
        val lat = prefs[Keys.FIX_LAT] ?: return null
        val lon = prefs[Keys.FIX_LON] ?: return null
        val time = prefs[Keys.FIX_TIME] ?: return null
        return GeoFix(
            lat = lat,
            lon = lon,
            fixTime = time,
            satellites = prefs[Keys.FIX_SATS],
            hdop = null,
            source = DataSource.DEVICE,
        )
    }

    suspend fun saveLastDeviceFix(fix: GeoFix) {
        if (fix.source != DataSource.DEVICE) return
        dataStore.edit { prefs ->
            prefs[Keys.FIX_LAT] = fix.lat
            prefs[Keys.FIX_LON] = fix.lon
            prefs[Keys.FIX_TIME] = fix.fixTime
            fix.satellites?.let { prefs[Keys.FIX_SATS] = it }
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            host = this[Keys.HOST] ?: defaults.host,
            port = this[Keys.PORT] ?: defaults.port,
            demoMode = this[Keys.DEMO] ?: defaults.demoMode,
            connectionEnabled = this[Keys.CONNECTION_ENABLED] ?: defaults.connectionEnabled,
            pollIntervalMs = this[Keys.POLL_INTERVAL] ?: defaults.pollIntervalMs,
            phoneAlarmOnDanger = this[Keys.ALARM_DANGER] ?: defaults.phoneAlarmOnDanger,
            phoneAlarmOnConnectionLost = this[Keys.ALARM_LOST] ?: defaults.phoneAlarmOnConnectionLost,
            warningDepthM = this[Keys.WARNING_DEPTH] ?: defaults.warningDepthM,
            dangerDepthM = this[Keys.DANGER_DEPTH] ?: defaults.dangerDepthM,
            submersionLimitSec = this[Keys.SUBMERSION_LIMIT] ?: defaults.submersionLimitSec,
            demoScenario = this[Keys.DEMO_SCENARIO]
                ?.let { name -> DemoScenario.entries.firstOrNull { it.name == name } }
                ?: defaults.demoScenario,
        )
    }

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val DEMO = booleanPreferencesKey("demo_mode")
        val CONNECTION_ENABLED = booleanPreferencesKey("connection_enabled")
        val POLL_INTERVAL = longPreferencesKey("poll_interval_ms")
        val ALARM_DANGER = booleanPreferencesKey("phone_alarm_danger")
        val ALARM_LOST = booleanPreferencesKey("phone_alarm_lost")
        val WARNING_DEPTH = doublePreferencesKey("warning_depth_m")
        val DANGER_DEPTH = doublePreferencesKey("danger_depth_m")
        val SUBMERSION_LIMIT = intPreferencesKey("submersion_limit_sec")
        val DEMO_SCENARIO = stringPreferencesKey("demo_scenario")
        val FIX_LAT = doublePreferencesKey("last_fix_lat")
        val FIX_LON = doublePreferencesKey("last_fix_lon")
        val FIX_TIME = longPreferencesKey("last_fix_time")
        val FIX_SATS = intPreferencesKey("last_fix_sats")
    }
}

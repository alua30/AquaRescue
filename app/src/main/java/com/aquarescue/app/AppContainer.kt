package com.aquarescue.app

import android.content.Context
import com.aquarescue.app.data.HistoryRepository
import com.aquarescue.app.data.MonitoringRepository
import com.aquarescue.app.data.SettingsRepository
import com.aquarescue.app.data.alarm.PhoneAlarmPlayer
import com.aquarescue.app.network.Esp32ApiClient
import com.aquarescue.app.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ручное внедрение зависимостей: единственные экземпляры репозиториев на всё приложение. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Область жизни приложения: опрос ESP32 продолжается при переключении экранов. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val networkMonitor = NetworkMonitor(appContext)
    private val apiClient = Esp32ApiClient(networkMonitor)

    val settingsRepository = SettingsRepository(appContext, applicationScope)
    val historyRepository = HistoryRepository(appContext, applicationScope)
    val monitoringRepository = MonitoringRepository(
        scope = applicationScope,
        settingsRepository = settingsRepository,
        historyRepository = historyRepository,
        apiClient = apiClient,
        alarmPlayer = PhoneAlarmPlayer(),
    )
}

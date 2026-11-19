package com.aquarescue.app.presentation

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aquarescue.app.AppContainer
import com.aquarescue.app.AquaRescueApp

/** Фабрика ViewModel'ей: зависимости берутся из [AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            HomeViewModel(c.monitoringRepository, c.settingsRepository, c.networkMonitor)
        }
        initializer { MapViewModel(container().monitoringRepository) }
        initializer { HistoryViewModel(container().historyRepository) }
        initializer {
            val c = container()
            SettingsViewModel(c.settingsRepository, c.monitoringRepository)
        }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as AquaRescueApp).container

package com.aquarescue.app.presentation

import androidx.lifecycle.ViewModel
import com.aquarescue.app.data.MonitoringRepository
import com.aquarescue.app.model.MonitorState
import kotlinx.coroutines.flow.StateFlow

class MapViewModel(monitoring: MonitoringRepository) : ViewModel() {
    val state: StateFlow<MonitorState> = monitoring.state
}

package com.aquarescue.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aquarescue.app.data.HistoryRepository
import com.aquarescue.app.model.EventCategory
import com.aquarescue.app.model.HistoryEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val loading: Boolean = true,
    val totalCount: Int = 0,
    val filter: EventCategory? = null,
    val events: List<HistoryEvent> = emptyList(),
)

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {

    private val filter = MutableStateFlow<EventCategory?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(history.events, history.loaded, filter) { events, loaded, f ->
        HistoryUiState(
            loading = !loaded,
            totalCount = events.size,
            filter = f,
            events = if (f == null) events else events.filter { it.type.category == f },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(category: EventCategory?) {
        filter.value = category
    }

    fun clear() = history.clear()
}

package com.aquarescue.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.EventCategory
import com.aquarescue.app.model.EventType
import com.aquarescue.app.model.HistoryEvent
import com.aquarescue.app.presentation.AppViewModelProvider
import com.aquarescue.app.presentation.HistoryViewModel
import com.aquarescue.app.ui.components.DemoTag
import com.aquarescue.app.ui.components.EmptyState
import com.aquarescue.app.ui.components.MarineCard
import com.aquarescue.app.ui.theme.MarinePrimary
import com.aquarescue.app.ui.theme.MarineSurfaceHigh
import com.aquarescue.app.ui.theme.Radius
import com.aquarescue.app.ui.theme.Spacing
import com.aquarescue.app.ui.theme.StatusDanger
import com.aquarescue.app.ui.theme.StatusLost
import com.aquarescue.app.ui.theme.StatusNormal
import com.aquarescue.app.ui.theme.StatusWarning
import com.aquarescue.app.ui.theme.TextPrimary
import com.aquarescue.app.ui.theme.TextSecondary
import com.aquarescue.app.ui.theme.TextTertiary
import com.aquarescue.app.util.Fmt
import com.aquarescue.app.ui.text.details
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.ui.text.title
import com.aquarescue.app.ui.text.titleRes
import com.aquarescue.app.R
import androidx.compose.ui.res.stringResource

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.s, top = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    if (state.totalCount == 0) {
                        stringResource(R.string.history_empty_log)
                    } else {
                        stringResource(R.string.history_total, state.totalCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = { confirmClear = true }, enabled = state.totalCount > 0) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.history_clear_a11y))
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { CategoryChip(stringResource(R.string.history_filter_all), state.filter == null) { viewModel.setFilter(null) } }
            items(EventCategory.entries) { c ->
                CategoryChip(stringResource(c.titleRes()), state.filter == c) { viewModel.setFilter(if (state.filter == c) null else c) }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.totalCount == 0 -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.EventNote,
                title = stringResource(R.string.history_empty_title),
                message = stringResource(R.string.history_empty_text),
                modifier = Modifier.padding(top = Spacing.xl),
            )
            state.events.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.EventNote,
                title = stringResource(R.string.history_empty_filter_title),
                message = stringResource(R.string.history_empty_filter_text),
                modifier = Modifier.padding(top = Spacing.xl),
            )
            else -> EventList(state.events)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MarineSurfaceHigh,
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_text, state.totalCount)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clear(); confirmClear = false },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger, contentColor = Color.White),
                ) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MarinePrimary.copy(alpha = 0.18f),
            selectedLabelColor = MarinePrimary,
            labelColor = TextSecondary,
        ),
    )
}

@Composable
private fun EventList(events: List<HistoryEvent>) {
    val res = resources()
    val grouped = remember(events) { events.groupBy { Fmt.localDateOf(it.timestamp) } }
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        grouped.forEach { (date, dayEvents) ->
            item(key = "header-$date") {
                Text(
                    Fmt.day(res, date).uppercase(Fmt.locale(res)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.s, bottom = Spacing.xs),
                )
            }
            items(dayEvents, key = { it.id }) { EventItem(it) }
        }
    }
}

@Composable
private fun EventItem(event: HistoryEvent) {
    val res = resources()
    val (icon, color) = eventVisual(event)
    MarineCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(Radius.control)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.title(res), style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f, fill = false))
                    if (event.source == DataSource.DEMO) {
                        Spacer(Modifier.width(Spacing.s))
                        DemoTag()
                    }
                }
                event.details(res)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.width(Spacing.s))
            Text(Fmt.time(event.timestamp), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

private fun eventVisual(event: HistoryEvent): Pair<ImageVector, Color> = when (event.type) {
    EventType.DEPTH_CHANGE -> {
        (if (event.flag) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown) to MarinePrimary
    }
    EventType.GPS_FIX_ACQUIRED -> Icons.Rounded.GpsFixed to StatusNormal
    EventType.GPS_FIX_LOST -> Icons.Rounded.GpsOff to StatusWarning
    EventType.GPS_UPDATE -> Icons.Rounded.MyLocation to MarinePrimary
    EventType.SOS_DEVICE, EventType.SOS_SENT -> Icons.Rounded.Sos to StatusDanger
    EventType.SOS_FAILED -> Icons.Rounded.ReportProblem to StatusDanger
    EventType.SOS_CANCELLED, EventType.SOS_CLEARED -> Icons.Rounded.Sos to StatusLost
    EventType.WARNING -> Icons.Rounded.Warning to StatusWarning
    EventType.DANGER -> Icons.Rounded.Error to StatusDanger
    EventType.NORMAL_RESTORED -> Icons.Rounded.CheckCircle to StatusNormal
    EventType.SENSOR_FAULT -> Icons.Rounded.SyncProblem to StatusWarning
    EventType.CONNECTION_LOST -> Icons.Rounded.CloudOff to StatusDanger
    EventType.CONNECTION_RESTORED -> Icons.Rounded.Wifi to StatusNormal
    EventType.CONNECTED -> Icons.Rounded.Link to StatusNormal
}

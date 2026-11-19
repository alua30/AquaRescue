package com.aquarescue.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.presentation.AppViewModelProvider
import com.aquarescue.app.presentation.HomeUiState
import com.aquarescue.app.presentation.HomeViewModel
import com.aquarescue.app.ui.components.DemoBanner
import com.aquarescue.app.ui.components.rememberNow
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.ui.theme.Spacing

/** Высота экрана, начиная с которой главный экран помещается без прокрутки. */
private val COMPACT_HEIGHT_THRESHOLD = 600.dp

@Composable
fun HomeScreen(
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val res = resources()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.resolve(res))
            viewModel.messageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeContent(
            state = state,
            onSosClick = viewModel::onSosClick,
            onSoundClick = viewModel::toggleDeviceSound,
            onOpenMap = onOpenMap,
            onOpenSettings = onOpenSettings,
            onRetry = viewModel::retryConnection,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Spacing.l))
    }

    SosDialog(
        state = state.sosDialog,
        demo = state.monitor.source == DataSource.DEMO,
        onConfirm = viewModel::confirmSos,
        onDismiss = viewModel::dismissSosDialog,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSosClick: () -> Unit,
    onSoundClick: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val now = rememberNow()
    val monitor = state.monitor
    val demo = monitor.source == DataSource.DEMO

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val roomy = maxHeight >= COMPACT_HEIGHT_THRESHOLD
        val columnModifier = if (roomy) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        }
        Column(
            columnModifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            HomeHeader(monitor = monitor, wifi = state.wifi, onConnectionClick = onOpenSettings)
            if (demo) DemoBanner()

            SystemStatusCard(monitor = monitor, now = now, onRetry = onRetry, onOpenSettings = onOpenSettings)

            DepthGaugeCard(
                monitor = monitor,
                settings = state.settings,
                now = now,
                modifier = if (roomy) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(250.dp),
            )

            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                DepthCard(monitor = monitor, modifier = Modifier.weight(1f).fillMaxHeight())
                GpsCard(monitor = monitor, now = now, onOpenMap = onOpenMap, modifier = Modifier.weight(1f).fillMaxHeight())
            }

            ActionRow(
                monitor = monitor,
                soundBusy = state.soundBusy,
                onSosClick = onSosClick,
                onSoundClick = onSoundClick,
            )
        }
    }
}

package com.aquarescue.app.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.NetworkWifi1Bar
import androidx.compose.material.icons.rounded.NetworkWifi2Bar
import androidx.compose.material.icons.rounded.NetworkWifi3Bar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aquarescue.app.R
import com.aquarescue.app.model.AppSettings
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.MonitorState
import com.aquarescue.app.network.PhoneWifiState
import com.aquarescue.app.ui.components.CardHeader
import com.aquarescue.app.ui.components.DemoTag
import com.aquarescue.app.ui.components.DepthGauge
import com.aquarescue.app.ui.components.LifebuoyLogo
import com.aquarescue.app.ui.components.MarineCard
import com.aquarescue.app.ui.components.StatusPill
import com.aquarescue.app.ui.components.connectionColor
import com.aquarescue.app.ui.components.systemVisual
import com.aquarescue.app.ui.text.message
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.ui.text.text
import com.aquarescue.app.ui.text.titleRes
import com.aquarescue.app.ui.theme.MarineSurfaceHigh
import com.aquarescue.app.ui.theme.Radius
import com.aquarescue.app.ui.theme.Spacing
import com.aquarescue.app.ui.theme.StatusDanger
import com.aquarescue.app.ui.theme.StatusDemo
import com.aquarescue.app.ui.theme.StatusLost
import com.aquarescue.app.ui.theme.StatusNormal
import com.aquarescue.app.ui.theme.StatusWarning
import com.aquarescue.app.ui.theme.TextPrimary
import com.aquarescue.app.ui.theme.TextSecondary
import com.aquarescue.app.ui.theme.TextTertiary
import com.aquarescue.app.util.Fmt

// ------------------------------------------------------------------ Шапка

@Composable
fun HomeHeader(
    monitor: MonitorState,
    wifi: PhoneWifiState,
    onConnectionClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LifebuoyLogo(size = 34.dp)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                monitor.endpointLabel?.let { stringResource(R.string.home_subtitle_device, it) }
                    ?: stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        WifiIndicator(wifi = wifi, rssi = monitor.snapshot?.wifi?.rssi?.takeIf { monitor.isOnline })
        Spacer(Modifier.width(Spacing.s))
        Surface(onClick = onConnectionClick, shape = RoundedCornerShape(50), color = Color.Transparent) {
            if (monitor.source == DataSource.DEMO) {
                StatusPill(text = stringResource(R.string.home_pill_demo), color = StatusDemo)
            } else {
                StatusPill(
                    text = stringResource(monitor.connection.titleRes()),
                    color = connectionColor(monitor.connection),
                )
            }
        }
    }
}

/** Индикатор Wi-Fi: подключён ли телефон к Wi-Fi и уровень сигнала ESP32 (RSSI). */
@Composable
private fun WifiIndicator(wifi: PhoneWifiState, rssi: Int?) {
    val res = resources()
    val rssiText = rssi?.let { Fmt.rssi(res, it) }
    val (icon, color) = when {
        !wifi.connected -> Icons.Rounded.WifiOff to StatusLost
        rssi == null -> Icons.Rounded.SignalWifi4Bar to MaterialTheme.colorScheme.primary
        rssi >= -60 -> Icons.Rounded.SignalWifi4Bar to StatusNormal
        rssi >= -70 -> Icons.Rounded.NetworkWifi3Bar to StatusNormal
        rssi >= -80 -> Icons.Rounded.NetworkWifi2Bar to StatusWarning
        else -> Icons.Rounded.NetworkWifi1Bar to StatusDanger
    }
    val label = when {
        !wifi.connected -> stringResource(R.string.home_wifi_off)
        rssiText != null -> rssiText
        else -> stringResource(R.string.home_wifi)
    }
    val description = if (wifi.connected) {
        stringResource(R.string.a11y_wifi_on, label)
    } else {
        stringResource(R.string.a11y_wifi_off)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
    }
}

// --------------------------------------------------------- Состояние системы

@Composable
fun SystemStatusCard(
    monitor: MonitorState,
    now: Long,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val res = resources()
    val visual = systemVisual(monitor)
    val color by animateColorAsState(visual.color, label = "statusColor")
    val detail = when {
        monitor.alarm.reasons.isNotEmpty() -> monitor.alarm.reasons.first().text(res)
        monitor.connection == ConnectionStatus.DISABLED -> stringResource(R.string.home_detail_disabled)
        !monitor.hasData && monitor.connection == ConnectionStatus.CONNECTING ->
            if (monitor.source == DataSource.DEMO) {
                stringResource(R.string.home_detail_demo_starting)
            } else {
                stringResource(R.string.home_detail_connecting, monitor.endpointLabel.orEmpty())
            }
        monitor.connection == ConnectionStatus.RECONNECTING ->
            stringResource(R.string.home_detail_reconnecting, monitor.lastError?.message(res).orEmpty())
        else -> stringResource(R.string.home_detail_ok)
    }
    val updated = monitor.lastUpdateAt?.let { stringResource(R.string.home_updated, Fmt.ago(res, it, now)) }
        ?: stringResource(R.string.home_no_data_yet)

    MarineCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = color.copy(alpha = 0.45f),
        contentPadding = Spacing.m,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(Radius.control)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(visual.icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                // Код состояния над заголовком: длинный CONNECTION_LOST не переносится.
                Text(stringResource(visual.code), style = MaterialTheme.typography.labelSmall, color = TextTertiary, maxLines = 1)
                Text(stringResource(visual.title), style = MaterialTheme.typography.titleMedium, color = color)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(updated, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            when (monitor.connection) {
                ConnectionStatus.LOST, ConnectionStatus.RECONNECTING ->
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.common_retry))
                    }
                ConnectionStatus.DISABLED ->
                    TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(stringResource(R.string.nav_settings))
                    }
                else -> Unit
            }
        }
    }
}

// ------------------------------------------------------------ Индикатор глубины

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepthGaugeCard(
    monitor: MonitorState,
    settings: AppSettings,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val res = resources()
    val snapshot = monitor.snapshot
    val depth = snapshot?.depth?.depthM?.takeIf { snapshot.depth.valid }
    val visual = systemVisual(monitor)
    val stale = monitor.connection == ConnectionStatus.LOST
    val caption = when {
        snapshot == null -> stringResource(R.string.gauge_no_data)
        !snapshot.depth.valid -> stringResource(R.string.gauge_sensor_unavailable)
        stale -> stringResource(R.string.gauge_last_value)
        monitor.submergedSince != null ->
            stringResource(R.string.gauge_underwater, Fmt.duration(res, now - monitor.submergedSince))
        else -> stringResource(R.string.gauge_current_depth)
    }.uppercase(Fmt.locale(res))

    MarineCard(modifier = modifier, contentPadding = Spacing.m) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            DepthGauge(
                depthM = depth,
                warningDepthM = settings.warningDepthM,
                dangerDepthM = settings.dangerDepthM,
                color = if (stale) StatusLost else visual.color,
                caption = caption,
                modifier = Modifier.size(side),
            )
        }
        // FlowRow: на узких экранах или с длинными переводами легенда переносится на вторую строку.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            LegendDot(StatusWarning, stringResource(R.string.gauge_legend_warning, Fmt.depth(res, settings.warningDepthM)))
            LegendDot(StatusDanger, stringResource(R.string.gauge_legend_danger, Fmt.depth(res, settings.dangerDepthM)))
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

// ---------------------------------------------------------- Глубина и GPS

@Composable
fun DepthCard(monitor: MonitorState, modifier: Modifier = Modifier) {
    val res = resources()
    val depthData = monitor.snapshot?.depth
    val demo = monitor.source == DataSource.DEMO
    val dash = stringResource(R.string.common_dash)
    MarineCard(modifier = modifier, contentPadding = Spacing.m) {
        CardHeader(Icons.Rounded.Straighten, stringResource(R.string.home_depth_title), trailing = if (demo) ({ DemoTag() }) else null)
        Spacer(Modifier.height(Spacing.s))
        val value = when {
            depthData == null -> dash
            !depthData.valid || depthData.depthM == null -> stringResource(R.string.common_na)
            else -> Fmt.depthValue(res, depthData.depthM)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, maxLines = 1)
            if (depthData?.depthM != null) {
                Text(
                    " " + stringResource(R.string.unit_meters_short),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        SmallLine(stringResource(R.string.home_depth_max), monitor.maxDepthM?.let { Fmt.depth(res, it) } ?: dash)
        SmallLine(stringResource(R.string.home_depth_water), depthData?.temperatureC?.let { Fmt.temperature(res, it) } ?: dash)
        SmallLine(stringResource(R.string.home_depth_pressure), depthData?.pressureMbar?.let { Fmt.pressure(res, it) } ?: dash)
    }
}

@Composable
fun GpsCard(
    monitor: MonitorState,
    now: Long,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = resources()
    val gps = monitor.snapshot?.gps
    val fix = monitor.lastKnownFix
    val live = gps?.fix == true && monitor.connection != ConnectionStatus.LOST
    val (statusText, statusColor, icon) = when {
        fix == null && monitor.snapshot == null ->
            Triple(stringResource(R.string.gps_no_data), StatusLost, Icons.Rounded.GpsOff)
        fix == null -> Triple(stringResource(R.string.gps_searching), StatusWarning, Icons.Rounded.GpsNotFixed)
        live -> Triple(stringResource(R.string.gps_signal_ok), StatusNormal, Icons.Rounded.GpsFixed)
        else -> Triple(
            stringResource(R.string.gps_last_known_ago, Fmt.ago(res, fix.fixTime, now)),
            StatusWarning,
            Icons.Rounded.GpsNotFixed,
        )
    }
    MarineCard(modifier = modifier, onClick = onOpenMap, contentPadding = Spacing.m) {
        CardHeader(
            icon, stringResource(R.string.home_gps_title),
            tint = statusColor,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_open_map),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        Spacer(Modifier.height(Spacing.s))
        if (fix != null) {
            val coordColor = if (live) TextPrimary else TextSecondary
            Text(Fmt.coordinate(fix.lat), style = MaterialTheme.typography.titleMedium, color = coordColor, maxLines = 1)
            Text(Fmt.coordinate(fix.lon), style = MaterialTheme.typography.titleMedium, color = coordColor, maxLines = 1)
        } else {
            Text(stringResource(R.string.common_dash), style = MaterialTheme.typography.headlineMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor, maxLines = 2)
        Text(stringResource(R.string.home_open_map), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SmallLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f).padding(end = Spacing.xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 1)
    }
}

// ------------------------------------------------------------ Кнопки действий

@Composable
fun ActionRow(
    monitor: MonitorState,
    soundBusy: Boolean,
    onSosClick: () -> Unit,
    onSoundClick: () -> Unit,
) {
    val sosActive = monitor.snapshot?.sos?.active == true
    val muted = monitor.snapshot?.audio?.muted
    val soundEnabled = muted != null && monitor.isOnline && !soundBusy

    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val sosModifier = Modifier.weight(1f).fillMaxSize().heightIn(min = 64.dp)
        if (sosActive) {
            OutlinedButton(
                onClick = onSosClick,
                modifier = sosModifier,
                shape = RoundedCornerShape(Radius.card),
                border = BorderStroke(2.dp, StatusDanger),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = StatusDanger.copy(alpha = 0.12f), contentColor = StatusDanger),
                contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sos_active), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.sos_tap_to_cancel), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        } else {
            Button(
                onClick = onSosClick,
                modifier = sosModifier,
                shape = RoundedCornerShape(Radius.card),
                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sos), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.sos_button_subtitle), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
        FilledTonalButton(
            onClick = onSoundClick,
            enabled = soundEnabled,
            modifier = Modifier.width(108.dp).fillMaxSize().heightIn(min = 64.dp),
            shape = RoundedCornerShape(Radius.card),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MarineSurfaceHigh, contentColor = TextPrimary),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = Spacing.s),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (soundBusy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (muted == true) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    stringResource(
                        when (muted) {
                            null -> R.string.home_sound
                            true -> R.string.home_sound_off
                            false -> R.string.home_sound_on
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

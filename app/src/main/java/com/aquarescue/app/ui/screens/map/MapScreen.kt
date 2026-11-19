package com.aquarescue.app.ui.screens.map

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aquarescue.app.R
import com.aquarescue.app.model.ConnectionStatus
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.GeoFix
import com.aquarescue.app.model.MonitorState
import com.aquarescue.app.presentation.AppViewModelProvider
import com.aquarescue.app.presentation.MapViewModel
import com.aquarescue.app.ui.components.CardHeader
import com.aquarescue.app.ui.components.DemoBanner
import com.aquarescue.app.ui.components.EmptyState
import com.aquarescue.app.ui.components.InfoRow
import com.aquarescue.app.ui.components.MarineCard
import com.aquarescue.app.ui.components.rememberNow
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.ui.theme.MarineBackground
import com.aquarescue.app.ui.theme.Radius
import com.aquarescue.app.ui.theme.Spacing
import com.aquarescue.app.ui.theme.StatusLost
import com.aquarescue.app.ui.theme.StatusNormal
import com.aquarescue.app.ui.theme.StatusWarning
import com.aquarescue.app.ui.theme.TextPrimary
import com.aquarescue.app.ui.theme.TextSecondary
import com.aquarescue.app.util.Fmt

private enum class GpsSignal { LIVE, STALE, NO_LINK, NONE }

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    val res = resources()
    val dash = stringResource(R.string.common_dash)

    val fix = state.lastKnownFix
    val gps = state.snapshot?.gps
    val signal = when {
        fix == null -> GpsSignal.NONE
        state.connection == ConnectionStatus.LOST || state.connection == ConnectionStatus.DISABLED || !state.hasData -> GpsSignal.NO_LINK
        gps?.fix == true -> GpsSignal.LIVE
        else -> GpsSignal.STALE
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.map_title), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        if (state.source == DataSource.DEMO) DemoBanner(text = stringResource(R.string.map_demo_banner))

        GpsStatusCard(signal = signal, state = state, fix = fix, now = now)

        if (fix == null) {
            MarineCard(Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Rounded.LocationOff,
                    title = stringResource(R.string.map_empty_title),
                    message = stringResource(if (state.hasData) R.string.map_empty_searching else R.string.map_empty_no_data),
                )
            }
            return@Column
        }

        MarineCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
            CardHeader(Icons.Rounded.Timeline, stringResource(R.string.map_track_title))
            Spacer(Modifier.size(Spacing.s))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(Radius.control))
                    .background(MarineBackground),
            ) {
                TrackPlot(
                    track = state.track,
                    lastFix = fix,
                    live = signal == GpsSignal.LIVE,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.size(Spacing.s))
            Text(stringResource(R.string.map_track_hint), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        MarineCard(Modifier.fillMaxWidth()) {
            CardHeader(
                Icons.Rounded.Place,
                stringResource(if (signal == GpsSignal.LIVE) R.string.map_current_coords else R.string.map_last_known_coords),
                tint = if (signal == GpsSignal.LIVE) StatusNormal else StatusWarning,
            )
            Spacer(Modifier.size(Spacing.s))
            InfoRow(stringResource(R.string.map_latitude), "${Fmt.coordinate(fix.lat)}\n${Fmt.latDms(res, fix.lat)}")
            InfoRow(stringResource(R.string.map_longitude), "${Fmt.coordinate(fix.lon)}\n${Fmt.lonDms(res, fix.lon)}")
            InfoRow(stringResource(R.string.map_last_update), Fmt.dateTime(res, fix.fixTime))
            InfoRow(
                stringResource(R.string.map_age),
                Fmt.ago(res, fix.fixTime, now),
                valueColor = if (signal == GpsSignal.LIVE) TextPrimary else StatusWarning,
            )
            InfoRow(stringResource(R.string.map_satellites), fix.satellites?.toString() ?: dash)
            InfoRow(stringResource(R.string.map_hdop), fix.hdop?.let { Fmt.decimal(res, it, 1) } ?: dash)
            InfoRow(
                stringResource(R.string.map_source),
                stringResource(if (fix.source == DataSource.DEMO) R.string.map_source_demo else R.string.map_source_device),
            )
        }

        CoordinateActions(fix = fix, stale = signal != GpsSignal.LIVE)
        Spacer(Modifier.size(Spacing.s))
    }
}

@Composable
private fun GpsStatusCard(signal: GpsSignal, state: MonitorState, fix: GeoFix?, now: Long) {
    val res = resources()
    val sats = state.snapshot?.gps?.satellites
    val hdop = state.snapshot?.gps?.hdop
    val ago = fix?.let { Fmt.ago(res, it.fixTime, now) }.orEmpty()
    val (icon, color, title, text) = when (signal) {
        GpsSignal.LIVE -> Quad(
            Icons.Rounded.GpsFixed, StatusNormal, stringResource(R.string.map_status_live),
            listOfNotNull(
                sats?.let { stringResource(R.string.map_status_satellites, it) },
                hdop?.let { stringResource(R.string.map_status_hdop, Fmt.decimal(res, it, 1)) },
                state.lastUpdateAt?.let { stringResource(R.string.map_status_updated_at, Fmt.time(it)) },
            ).joinToString(" · "),
        )
        GpsSignal.STALE -> Quad(
            Icons.Rounded.GpsNotFixed, StatusWarning, stringResource(R.string.map_status_stale),
            stringResource(R.string.map_status_stale_text, ago),
        )
        GpsSignal.NO_LINK -> Quad(
            Icons.Rounded.LocationOff, StatusLost, stringResource(R.string.map_status_no_link),
            stringResource(R.string.map_status_no_link_text, ago),
        )
        GpsSignal.NONE -> Quad(
            Icons.Rounded.LocationOff, StatusLost, stringResource(R.string.map_status_none),
            stringResource(if (state.hasData) R.string.map_status_none_text else R.string.map_status_none_no_data),
        )
    }
    MarineCard(Modifier.fillMaxWidth(), borderColor = color.copy(alpha = 0.45f), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Spacing.m))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                if (text.isNotBlank()) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun CoordinateActions(fix: GeoFix, stale: Boolean) {
    val context = LocalContext.current
    val res = resources()
    // Все тексты берутся в composition: при смене языка они пересчитываются.
    val clipLabel = stringResource(if (stale) R.string.map_last_known_coords else R.string.map_current_coords)
    val copiedMessage = stringResource(R.string.map_copied)
    val noMapsMessage = stringResource(R.string.map_no_maps_app)
    val chooserTitle = stringResource(R.string.map_share_chooser)
    val header = stringResource(if (stale) R.string.map_share_header_stale else R.string.map_share_header)
    val fixTime = stringResource(R.string.map_share_fix_time, Fmt.dateTime(res, fix.fixTime))
    val demoNote = if (fix.source == DataSource.DEMO) stringResource(R.string.map_share_demo_note) else null
    val shareText = listOfNotNull(
        header,
        Fmt.coordinates(fix.lat, fix.lon),
        fixTime,
        "https://maps.google.com/?q=${Fmt.coordinate(fix.lat)},${Fmt.coordinate(fix.lon)}",
        demoNote,
    ).joinToString(separator = "\n")

    Button(
        onClick = { openInMaps(context, fix, noMapsMessage) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.s))
        Text(stringResource(R.string.map_open_in_maps))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        ActionButton(Icons.Rounded.ContentCopy, stringResource(R.string.map_copy), Modifier.weight(1f)) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, Fmt.coordinates(fix.lat, fix.lon)))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }
        ActionButton(Icons.Rounded.Share, stringResource(R.string.map_share), Modifier.weight(1f)) {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.s))
        Text(text, textAlign = TextAlign.Center)
    }
}

private fun openInMaps(context: Context, fix: GeoFix, noMapsMessage: String) {
    val lat = Fmt.coordinate(fix.lat)
    val lon = Fmt.coordinate(fix.lon)
    val geo = Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon(AquaRescue)".toUri())
    try {
        context.startActivity(geo)
    } catch (_: ActivityNotFoundException) {
        // Нет приложения карт — открываем веб-карту в браузере.
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://maps.google.com/?q=$lat,$lon".toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, noMapsMessage, Toast.LENGTH_LONG).show()
        }
    }
}

private data class Quad(val icon: ImageVector, val color: Color, val title: String, val text: String)

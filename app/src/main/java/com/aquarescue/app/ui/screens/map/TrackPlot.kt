package com.aquarescue.app.ui.screens.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aquarescue.app.R
import com.aquarescue.app.domain.GeoMath
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.util.Fmt
import androidx.compose.ui.res.stringResource
import com.aquarescue.app.model.GeoFix
import com.aquarescue.app.ui.theme.MarineOutlineSoft
import com.aquarescue.app.ui.theme.MarinePrimary
import com.aquarescue.app.ui.theme.Spacing
import com.aquarescue.app.ui.theme.StatusNormal
import com.aquarescue.app.ui.theme.StatusWarning
import com.aquarescue.app.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.max

private val SCALE_STEPS_M = listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000)

/**
 * Схема перемещения жилета без картографической подложки (не требует API-ключа).
 * Центр — последняя известная точка; север сверху.
 */
@Composable
fun TrackPlot(
    track: List<GeoFix>,
    lastFix: GeoFix,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseFraction by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "pulseFraction",
    )

    val points = track.map { GeoMath.offsetMeters(lastFix.lat, lastFix.lon, it.lat, it.lon) }
    val extentM = (max(20.0, points.maxOfOrNull { max(abs(it.first), abs(it.second)) } ?: 0.0) * 1.15).toFloat()
    val markerColor = if (live) StatusNormal else StatusWarning

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val pxPerM = (minOf(widthPx, heightPx) / 2) / extentM
        val stepM = SCALE_STEPS_M.firstOrNull { it * pxPerM >= widthPx / 5 } ?: SCALE_STEPS_M.last()
        val res = resources()
        val scaleLabel = if (stepM >= 1000) Fmt.kilometersInt(res, stepM / 1000) else Fmt.metersInt(res, stepM)

        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)

            // Сетка
            val gridStep = size.minDimension / 6
            var x = center.x % gridStep
            while (x < size.width) {
                drawLine(MarineOutlineSoft, Offset(x, 0f), Offset(x, size.height), 1f)
                x += gridStep
            }
            var y = center.y % gridStep
            while (y < size.height) {
                drawLine(MarineOutlineSoft, Offset(0f, y), Offset(size.width, y), 1f)
                y += gridStep
            }

            // Траектория
            if (points.size >= 2) {
                val path = Path()
                points.forEachIndexed { i, (mx, my) ->
                    val px = center.x + mx.toFloat() * pxPerM
                    val py = center.y - my.toFloat() * pxPerM
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, MarinePrimary.copy(alpha = 0.8f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                val first = points.first()
                drawCircle(MarinePrimary, 4.dp.toPx(), Offset(center.x + first.first.toFloat() * pxPerM, center.y - first.second.toFloat() * pxPerM))
            }

            // Маркер последней известной позиции: сплошной и пульсирующий — сигнал есть,
            // пунктирный — устаревшие координаты.
            if (live) {
                drawCircle(markerColor.copy(alpha = 0.35f * (1 - pulseFraction)), 8.dp.toPx() + 18.dp.toPx() * pulseFraction, center)
                drawCircle(markerColor, 8.dp.toPx(), center)
                drawCircle(Color.White, 3.dp.toPx(), center)
            } else {
                drawCircle(markerColor, 9.dp.toPx(), center, style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                drawCircle(markerColor, 3.dp.toPx(), center)
            }

            // Масштабная линейка
            val barPx = stepM * pxPerM
            val barY = size.height - 12.dp.toPx()
            val barX = 14.dp.toPx()
            drawLine(TextSecondary, Offset(barX, barY), Offset(barX + barPx, barY), 2.dp.toPx())
            drawLine(TextSecondary, Offset(barX, barY - 4.dp.toPx()), Offset(barX, barY), 2.dp.toPx())
            drawLine(TextSecondary, Offset(barX + barPx, barY - 4.dp.toPx()), Offset(barX + barPx, barY), 2.dp.toPx())
        }
        Text(stringResource(R.string.map_north), style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.s))
        Text(scaleLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 18.dp))
    }
}

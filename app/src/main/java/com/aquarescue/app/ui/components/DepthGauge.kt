package com.aquarescue.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aquarescue.app.ui.theme.MarineSurfaceHighest
import com.aquarescue.app.ui.theme.StatusDanger
import com.aquarescue.app.ui.theme.StatusWarning
import com.aquarescue.app.ui.theme.TextPrimary
import com.aquarescue.app.ui.theme.TextSecondary
import com.aquarescue.app.R
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.util.Fmt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP = 270f

/**
 * Большой круговой индикатор глубины. Шкала 0…[maxDepthM], на ней отмечены
 * пороги предупреждения и опасности; цвет дуги соответствует состоянию системы.
 */
@Composable
fun DepthGauge(
    depthM: Double?,
    warningDepthM: Double,
    dangerDepthM: Double,
    color: Color,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val maxDepth = max(3.0, dangerDepthM * 2).toFloat()
    val fraction by animateFloatAsState(
        targetValue = ((depthM ?: 0.0).toFloat() / maxDepth).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "depth",
    )
    val arcColor by animateColorAsState(color, tween(400), label = "gaugeColor")
    val res = resources()
    val valueText = depthM?.let { Fmt.depthValue(res, it) }
    val description = if (valueText != null) {
        stringResource(R.string.a11y_depth, valueText)
    } else {
        stringResource(R.string.a11y_depth_unknown)
    }

    Box(
        modifier.aspectRatio(1f).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2 + size.minDimension * 0.04f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Фон шкалы
            drawArc(MarineSurfaceHighest, START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // Зоны предупреждения и опасности — тонкая полоса внутри шкалы
            val zoneStroke = stroke * 0.22f
            val zoneInset = inset + stroke * 0.95f
            val zoneSize = Size(size.width - zoneInset * 2, size.height - zoneInset * 2)
            val zoneTopLeft = Offset(zoneInset, zoneInset)
            val warnStart = (warningDepthM / maxDepth).toFloat().coerceIn(0f, 1f)
            val dangerStart = (dangerDepthM / maxDepth).toFloat().coerceIn(0f, 1f)
            drawArc(StatusWarning.copy(alpha = 0.55f), START_ANGLE + SWEEP * warnStart, SWEEP * (dangerStart - warnStart), false, zoneTopLeft, zoneSize, style = Stroke(zoneStroke))
            drawArc(StatusDanger.copy(alpha = 0.55f), START_ANGLE + SWEEP * dangerStart, SWEEP * (1f - dangerStart), false, zoneTopLeft, zoneSize, style = Stroke(zoneStroke))

            // Значение
            if (depthM != null) {
                drawArc(arcColor, START_ANGLE, max(SWEEP * fraction, 0.5f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Деления шкалы через каждые 0,5 м
            val ticks = (maxDepth / 0.5f).toInt()
            for (i in 0..ticks) {
                drawTick(i / ticks.toFloat(), inset - stroke * 0.9f, stroke * 0.35f, if (i % 2 == 0) 2.dp.toPx() else 1.dp.toPx())
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                buildAnnotatedString {
                    append(valueText ?: stringResource(R.string.common_dash))
                    withStyle(SpanStyle(fontSize = 18.sp, color = TextSecondary)) {
                        append(" ")
                        append(stringResource(R.string.unit_meters_short))
                    }
                },
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(0.62f),
            )
        }
    }
}

private fun DrawScope.drawTick(fraction: Float, radiusInset: Float, length: Float, width: Float) {
    val angle = Math.toRadians((START_ANGLE + SWEEP * fraction).toDouble())
    val center = Offset(size.width / 2, size.height / 2)
    val outer = size.minDimension / 2 - radiusInset.coerceAtLeast(0f)
    val inner = outer - length
    drawLine(
        TextSecondary.copy(alpha = 0.5f),
        Offset(center.x + (outer * cos(angle)).toFloat(), center.y + (outer * sin(angle)).toFloat()),
        Offset(center.x + (inner * cos(angle)).toFloat(), center.y + (inner * sin(angle)).toFloat()),
        strokeWidth = width,
    )
}

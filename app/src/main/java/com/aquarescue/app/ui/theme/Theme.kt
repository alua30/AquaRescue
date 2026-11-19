package com.aquarescue.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Приложение всегда использует тёмную морскую тему: интерфейс мониторинга
 * должен выглядеть одинаково на любом устройстве (без dynamic color).
 */
private val MarineColorScheme = darkColorScheme(
    primary = MarinePrimary,
    onPrimary = MarineOnPrimary,
    primaryContainer = MarinePrimaryContainer,
    onPrimaryContainer = TextPrimary,
    secondary = MarinePrimary,
    onSecondary = MarineOnPrimary,
    secondaryContainer = MarineSurfaceHighest,
    onSecondaryContainer = TextPrimary,
    tertiary = StatusNormal,
    background = MarineBackground,
    onBackground = TextPrimary,
    surface = MarineSurface,
    onSurface = TextPrimary,
    surfaceVariant = MarineSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = MarineBackground,
    surfaceContainerLow = MarineSurface,
    surfaceContainer = MarineSurface,
    surfaceContainerHigh = MarineSurfaceHigh,
    surfaceContainerHighest = MarineSurfaceHighest,
    outline = MarineOutline,
    outlineVariant = MarineOutlineSoft,
    error = StatusDanger,
    onError = MarineOnPrimary,
    errorContainer = StatusDanger.copy(alpha = 0.18f),
    onErrorContainer = StatusDanger,
)

/** Единые радиусы скругления: карточки — 16dp, элементы управления — 12dp. */
object Radius {
    val card = 16.dp
    val control = 12.dp
    val chip = 8.dp
}

/** Единая сетка отступов. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

private val MarineShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.chip),
    small = RoundedCornerShape(Radius.control),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AquaRescueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MarineColorScheme,
        typography = Typography,
        shapes = MarineShapes,
        content = content,
    )
}

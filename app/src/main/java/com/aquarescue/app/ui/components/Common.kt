package com.aquarescue.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aquarescue.app.R
import com.aquarescue.app.ui.theme.Radius
import com.aquarescue.app.ui.theme.Spacing
import com.aquarescue.app.ui.theme.StatusDanger
import com.aquarescue.app.ui.theme.StatusDemo
import com.aquarescue.app.ui.theme.TextPrimary
import com.aquarescue.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** Текущее время, обновляемое каждые [intervalMs] — для «N с назад». */
@Composable
fun rememberNow(intervalMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
    return now
}

/** Логотип AquaRescue — спасательный круг (как иконка приложения). */
@Composable
fun LifebuoyLogo(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.2f
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(Color.White, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        listOf(25f, 115f, 205f, 295f).forEach { start ->
            drawArc(StatusDanger, start, 40f, false, topLeft, arcSize, style = Stroke(stroke))
        }
    }
}

/** Полоса «ДЕМО-РЕЖИМ»: явно сообщает, что данные не от реального устройства. */
@Composable
fun DemoBanner(text: String = stringResource(R.string.demo_banner), modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .background(StatusDemo.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.m, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Science, contentDescription = null, tint = StatusDemo, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.s))
        // Без ограничения по строкам: длинные переводы переносятся, а не обрезаются.
        Text(text, style = MaterialTheme.typography.labelMedium, color = StatusDemo)
    }
}

/** Маленькая метка «ДЕМО» для отдельных значений и событий. */
@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.demo_tag),
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
        color = StatusDemo,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(StatusDemo.copy(alpha = 0.16f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Пустое состояние экрана. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.s))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Spacing.l))
            action()
        }
    }
}

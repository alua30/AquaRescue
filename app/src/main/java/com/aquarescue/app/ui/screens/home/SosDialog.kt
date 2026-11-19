package com.aquarescue.app.ui.screens.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.aquarescue.app.R
import com.aquarescue.app.model.CommandResult
import com.aquarescue.app.presentation.SosDialogState
import com.aquarescue.app.ui.text.message
import com.aquarescue.app.ui.text.resources
import com.aquarescue.app.ui.theme.MarineSurfaceHigh
import com.aquarescue.app.ui.theme.Spacing
import com.aquarescue.app.ui.theme.StatusDanger
import com.aquarescue.app.ui.theme.StatusDemo
import com.aquarescue.app.ui.theme.StatusNormal
import com.aquarescue.app.ui.theme.TextPrimary
import com.aquarescue.app.ui.theme.TextSecondary

/**
 * Диалоги сценария SOS: подтверждение → ожидание подтверждения ESP32 → результат.
 * Сообщение «SOS отправлен» показывается только после подтверждения устройством.
 */
@Composable
fun SosDialog(
    state: SosDialogState,
    demo: Boolean,
    onConfirm: (activate: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        SosDialogState.Hidden -> Unit
        is SosDialogState.Confirm -> ConfirmDialog(state.activate, demo, onConfirm, onDismiss)
        is SosDialogState.Sending -> SendingDialog(state.activate, demo)
        is SosDialogState.Result -> ResultDialog(state, onRetry = { onConfirm(state.activate) }, onDismiss = onDismiss)
    }
}

@Composable
private fun ConfirmDialog(
    activate: Boolean,
    demo: Boolean,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MarineSurfaceHigh,
        icon = { Icon(Icons.Rounded.Sos, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(36.dp)) },
        title = { Text(stringResource(if (activate) R.string.sos_confirm_title else R.string.sos_cancel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(if (activate) R.string.sos_confirm_text else R.string.sos_cancel_text),
                    color = TextPrimary,
                )
                if (demo) {
                    Text(stringResource(R.string.sos_confirm_demo_note), color = StatusDemo, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(activate) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger, contentColor = Color.White),
            ) { Text(stringResource(if (activate) R.string.sos_send else R.string.sos_cancel_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) } },
    )
}

@Composable
private fun SendingDialog(activate: Boolean, demo: Boolean) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = MarineSurfaceHigh,
        title = { Text(stringResource(if (activate) R.string.sos_sending else R.string.sos_cancelling)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(Spacing.l))
                Text(
                    stringResource(if (demo) R.string.sos_waiting_demo else R.string.sos_waiting_device),
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ResultDialog(
    state: SosDialogState.Result,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val res = resources()
    when (val r = state.result) {
        is CommandResult.Confirmed -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MarineSurfaceHigh,
            icon = {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (r.demo) StatusDemo else StatusNormal, modifier = Modifier.size(36.dp))
            },
            title = {
                Text(
                    stringResource(
                        when {
                            r.demo -> R.string.sos_result_demo_title
                            state.activate -> R.string.sos_result_confirmed_title
                            else -> R.string.sos_result_cancelled_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        when {
                            r.demo && state.activate -> R.string.sos_result_demo_activate
                            r.demo -> R.string.sos_result_demo_cancel
                            state.activate -> R.string.sos_result_device_activate
                            else -> R.string.sos_result_device_cancel
                        }
                    ),
                    color = TextPrimary,
                )
            },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
        )

        is CommandResult.Failed, is CommandResult.NotConnected -> {
            val error = if (r is CommandResult.Failed) r.error else (r as CommandResult.NotConnected).error
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MarineSurfaceHigh,
                icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(36.dp)) },
                title = { Text(stringResource(if (state.activate) R.string.sos_failed_title else R.string.sos_cancel_failed_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(stringResource(R.string.sos_failed_text), color = TextPrimary)
                        Text(error.message(res), color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                        if (state.activate) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(stringResource(R.string.sos_failed_hint), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDanger, contentColor = Color.White),
                    ) { Text(stringResource(R.string.common_retry)) }
                },
                dismissButton = {
                    Row {
                        if (state.activate) {
                            TextButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:112".toUri()))
                                } catch (_: ActivityNotFoundException) {
                                    // Устройство без функции звонков (например, планшет).
                                }
                            }) {
                                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.sos_call_112))
                            }
                        }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                    }
                },
            )
        }
    }
}

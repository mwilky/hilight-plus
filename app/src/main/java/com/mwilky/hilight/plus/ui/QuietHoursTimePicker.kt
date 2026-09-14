package com.mwilky.hilight.plus.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mwilky.hilight.plus.R
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).mod(24),
        initialMinute = initialMinutes.mod(60),
        is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.dialog_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}

fun formatClockMinutes(context: Context, minutes: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, (minutes / 60).mod(24))
        set(Calendar.MINUTE, minutes.mod(60))
        set(Calendar.SECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(cal.time)
}

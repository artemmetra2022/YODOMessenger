package app.yodo.messenger.features.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.yodo.messenger.R
import java.util.Calendar

@Composable
fun ReminderDialog(onDismiss: () -> Unit, onSchedule: (Long) -> Unit, onCancelReminder: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var pendingTime by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val at = pendingTime
        pendingTime = null
        if (granted && at != null) { onSchedule(at); onDismiss() }
        else error = context.getString(R.string.reminder_permission_required)
    }
    fun schedule(at: Long) {
        if (at <= System.currentTimeMillis()) {
            error = context.getString(R.string.reminder_future_required)
        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingTime = at
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            error = context.getString(R.string.reminder_permission_required)
        } else { onSchedule(at); onDismiss() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column {
                Text(stringResource(R.string.reminder_explanation))
                TextButton(onClick = { schedule(System.currentTimeMillis() + 60 * 60 * 1000L) }) {
                    Text(stringResource(R.string.reminder_in_hour))
                }
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                    }
                    schedule(calendar.timeInMillis)
                }) { Text(stringResource(R.string.reminder_at_twenty)) }
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    DatePickerDialog(context, { _, year, month, day ->
                        calendar.set(year, month, day)
                        TimePickerDialog(context, { _, hour, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute)
                            calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                            schedule(calendar.timeInMillis)
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }) { Text(stringResource(R.string.reminder_custom_time)) }
                error?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_close)) } },
        dismissButton = { TextButton(onClick = { onCancelReminder(); onDismiss() }) {
            Text(stringResource(R.string.reminder_cancel))
        } }
    )
}

package app.yodo.messenger.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Разделяет административные функции мессенджера и школы. */
enum class AdminArea {
    MESSENGER,
    SCHOOL
}

/** Общий переключатель разделов в верхней части админ-панели. */
@Composable
fun AdminAreaSwitcher(
    selectedArea: AdminArea,
    onMessengerClick: () -> Unit,
    onSchoolClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = if (selectedArea == AdminArea.MESSENGER) 0 else 1,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Tab(
            selected = selectedArea == AdminArea.MESSENGER,
            onClick = onMessengerClick,
            text = { Text("Мессенджер") }
        )
        Tab(
            selected = selectedArea == AdminArea.SCHOOL,
            onClick = onSchoolClick,
            text = { Text("Школа") }
        )
    }
}

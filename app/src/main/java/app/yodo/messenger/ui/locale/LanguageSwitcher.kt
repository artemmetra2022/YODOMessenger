package app.yodo.messenger.ui.locale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.AppLanguage
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Компактный переключатель языка (RU / EN) в виде двух чипов.
 * Значение общее для всего приложения (регистрация ⇄ настройки), см. LanguageViewModel.
 */
@Composable
fun LanguageSwitcher(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    // LocalContext.current в этом месте дерева может быть локализованным ContextImpl
    // (см. LocalizedApp.kt), а hiltViewModel() требует Activity-контекст. Поэтому
    // на время создания ViewModel временно восстанавливаем исходный Activity-контекст.
    val activityContext = LocalActivityContext.current
    val viewModel: LanguageViewModel
    if (activityContext != null) {
        CompositionLocalProvider(LocalContext provides activityContext) {
            viewModel = hiltViewModel()
        }
    } else {
        viewModel = hiltViewModel()
    }

    val currentLanguage by viewModel.currentLanguage.collectAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        listOf(AppLanguage.RU, AppLanguage.EN).forEach { language ->
            val isSelected = currentLanguage == language
            Text(
                text = stringResource(language.labelResId),
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .clickable { viewModel.setLanguage(language) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

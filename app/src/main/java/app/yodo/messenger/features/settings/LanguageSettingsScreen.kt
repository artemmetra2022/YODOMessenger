package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.data.local.AppLanguage
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (разделение настроек по категориям): «Язык» — выбор языка интерфейса.
 * Перенесено из бывшего монолитного SettingsScreen.kt без изменений поведения.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageSettingsScreen(
    onBackClick: () -> Unit,
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val colorTheme = LocalColorTheme.current

    val listState = rememberLazyListState()
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialAnchorId) {
        if (initialAnchorId != null) {
            kotlinx.coroutines.delay(250)
            highlightedAnchor = initialAnchorId
            anchorPositions[initialAnchorId]?.let { listState.animateScrollBy(it - 24f) }
            kotlinx.coroutines.delay(SETTINGS_HIGHLIGHT_DURATION_MS)
            if (highlightedAnchor == initialAnchorId) highlightedAnchor = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Язык", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_section_language),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_LANGUAGE, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Language,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppLanguage.entries.forEach { language ->
                                val isSelected = currentLanguage == language
                                Text(
                                    text = stringResource(language.labelResId),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colorTheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.setLanguage(language) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

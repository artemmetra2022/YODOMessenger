package app.yodo.messenger.features.update

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yodo.messenger.BuildConfig
import app.yodo.messenger.R
import app.yodo.messenger.features.settings.SettingsCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled history is readable offline; opening About does not issue extra network requests. */
@Composable
internal fun rememberReleaseHistory(): List<ReleaseHistoryEntry> {
    val context=LocalContext.current.applicationContext
    val history by produceState<List<ReleaseHistoryEntry>>(emptyList(),context) {
        value=withContext(Dispatchers.IO) {
            try { context.assets.open("release_history.json").bufferedReader().use { parseReleaseHistory(it.readText()) } }
            catch (_: java.io.IOException) { emptyList() }
        }
    }
    return history
}

@Composable
internal fun AboutIdentityCard(version: String, history: List<ReleaseHistoryEntry>) {
    val date=installedReleaseDate(version,BuildConfig.RELEASE_DATE,history)
    SettingsCard {
        Column(Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.illustration_yodo_about),
                contentDescription=stringResource(R.string.about_brand_image_description),
                modifier=Modifier.fillMaxWidth().aspectRatio(360f/168f),contentScale=ContentScale.Fit)
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("YodoMessenger",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(stringResource(R.string.about_brand_tagline),style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical=4.dp))
                Text(stringResource(R.string.about_current_version_format,version),
                    style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Text(if(date!=null)stringResource(R.string.about_release_date_format,date) else stringResource(R.string.about_release_date_unknown),
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AboutReleaseHistory(history: List<ReleaseHistoryEntry>, currentVersion: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.about_history_title),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            if(history.isEmpty())Text(stringResource(R.string.about_history_empty),style=MaterialTheme.typography.bodyMedium)
            (if(expanded)history else history.take(3)).forEachIndexed { index,entry ->
                if(index>0)HorizontalDivider()
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    // Vertical labels remain readable with large fonts and at 320 dp.
                    Text(stringResource(R.string.about_history_version,entry.version),style=MaterialTheme.typography.titleMedium,
                        color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                    if(entry.version==currentVersion.removePrefix("v"))Text(stringResource(R.string.about_history_installed),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                    releaseDateLabel(entry.date)?.let { Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(entry.summary,style=MaterialTheme.typography.bodyMedium)
                }
            }
            if(history.size>3)TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(if(expanded)R.string.about_history_collapse else R.string.about_history_expand))
            }
        }
    }
}

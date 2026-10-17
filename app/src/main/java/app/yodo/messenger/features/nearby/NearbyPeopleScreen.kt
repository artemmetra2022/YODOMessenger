package app.yodo.messenger.features.nearby

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.domain.model.NearbyPerson
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.UserAvatar
import org.osmdroid.config.Configuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NearbyPeopleScreen(
    onBackClick: () -> Unit,
    onPersonClick: (String) -> Unit,
    viewModel: NearbyPeopleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    val uiState by viewModel.uiState.collectAsState()
    val content = uiState as? NearbyUiState.Content
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionGranted = results.values.any { it }
        if (permissionGranted) viewModel.startSearching()
    }
    fun requestLocation() = permissionLauncher.launch(arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    ))
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidTileCache = context.cacheDir
        permissionGranted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (permissionGranted) viewModel.startSearching() else requestLocation()
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopSharingLocation() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.nearby_title), style = MaterialTheme.typography.titleLarge)
                        Text("Люди поблизости", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_back_cd))
                    }
                },
                actions = {
                    if (permissionGranted) {
                        IconButton(onClick = { viewModel.startSearching() },
                            enabled = uiState !is NearbyUiState.Loading && content?.isRefreshing != true) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.nearby_refresh_cd))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (permissionGranted) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        label = { Text("Карта") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        label = { Text("Список${content?.let { " · ${it.people.size}" }.orEmpty()}") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                }
            }
            when {
                !permissionGranted -> NearbyStatus(
                    stringResource(R.string.nearby_permission_hint), "Разрешить доступ", ::requestLocation)
                uiState is NearbyUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Ищем людей рядом…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                content == null -> NearbyStatus(stringResource(R.string.nearby_location_error),
                    "Повторить", { viewModel.startSearching() })
                else -> {
                    // A Column, not a Box: the summary must not overlap the map/list.
                    NearbySearchSummary(content)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (selectedTab == 1) {
                            NearbyPeopleMap(content, onPersonClick)
                        } else {
                            NearbyPeopleList(content, onPersonClick)
                        }
                        if (content.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyStatus(text: String, action: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun NearbySearchSummary(state: NearbyUiState.Content) {
    val updated = remember(state.searchedAtMillis) {
        if (state.searchedAtMillis > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.searchedAtMillis)) else "—"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Рядом ${state.people.size} · радиус ${state.radiusKm.toInt()} км",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (state.refreshFailed) "Не удалось обновить. Показаны предыдущие результаты."
            else "Обновлено в $updated" + if (state.myAccuracyMeters > 0) " · точность ±${state.myAccuracyMeters.toInt()} м" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (state.refreshFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NearbyPeopleList(state: NearbyUiState.Content, onPersonClick: (String) -> Unit) {
    if (state.people.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nearby_empty), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.people, key = { it.uid }) { person ->
                NearbyPersonCard(person, state.myLat, state.myLng, onClick = { onPersonClick(person.uid) })
            }
        }
    }
}

@Composable
internal fun NearbyPersonCard(person: NearbyPerson, myLat: Double, myLng: Double,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UserAvatar(displayName = person.displayName, photoUrl = person.photoUrl,
                avatarBase64 = person.avatarBase64, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(person.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatNearbyDistance(person.distanceMeters) + " · " + nearbyDirection(myLat, myLng, person.latitude, person.longitude),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, "Открыть профиль", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

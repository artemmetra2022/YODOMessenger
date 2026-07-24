package app.yodo.messenger.features.nearby

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.NearbyPerson
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.YodoPrimary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun NearbyPeopleScreen(
    onBackClick: () -> Unit,
    onPersonClick: (String) -> Unit,
    viewModel: NearbyPeopleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = список, 1 = карта

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionGranted = results.values.any { it } // хватит и приблизительной геолокации
        if (permissionGranted) viewModel.startSearching()
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidTileCache = context.cacheDir
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSharingLocation() }
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кто рядом", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (permissionGranted) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Список") },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Карта") },
                        icon = { Icon(Icons.Filled.Map, contentDescription = null) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !permissionGranted -> {
                        Text(
                            text = "Нужен доступ к геолокации, чтобы найти людей рядом.",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    uiState is NearbyUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    uiState is NearbyUiState.LocationUnavailable -> {
                        Text(
                            text = "Не удалось определить геопозицию. Проверь, включена ли геолокация на телефоне.",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    else -> {
                        val state = uiState as NearbyUiState.Content
                        if (selectedTab == 0) {
                            NearbyPeopleList(state.people, onPersonClick)
                        } else {
                            NearbyPeopleMap(state.myLat, state.myLng, state.people)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyPeopleList(people: List<NearbyPerson>, onPersonClick: (String) -> Unit) {
    if (people.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Пока никого не видно поблизости.\nПопробуй позже — люди появляются, когда открывают этот экран.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(people, key = { it.uid }) { person ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPersonClick(person.uid) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    displayName = person.displayName,
                    photoUrl = person.photoUrl,
                    avatarBase64 = person.avatarBase64,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = person.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(text = formatDistance(person.distanceMeters), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NearbyPeopleMap(myLat: Double, myLng: Double, people: List<NearbyPerson>) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
                controller.setCenter(GeoPoint(myLat, myLng))

                // Отмечаем себя
                val myMarker = Marker(this)
                myMarker.position = GeoPoint(myLat, myLng)
                myMarker.title = "Вы здесь"
                overlays.add(myMarker)

                // Отмечаем найденных людей
                people.forEach { person ->
                    val marker = Marker(this)
                    marker.position = GeoPoint(person.latitude, person.longitude)
                    marker.title = person.displayName
                    marker.snippet = formatDistance(person.distanceMeters)
                    overlays.add(marker)
                }
            }
        }
    )
}

private fun formatDistance(meters: Double): String {
    return if (meters < 1000) {
        "${meters.toInt()} м"
    } else {
        "%.1f км".format(meters / 1000)
    }
}

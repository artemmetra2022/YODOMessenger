package app.yodo.messenger.features.nearby

import android.Manifest
import android.graphics.Paint
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.NearbyPerson
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.YodoPrimary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
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
                title = { Text(stringResource(R.string.nearby_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                    }
                },
                // НОВОЕ (баг 13): ручное обновление поиска — повторно определяет геопозицию
                // и пересобирает список людей поблизости.
                actions = {
                    if (permissionGranted) {
                        IconButton(onClick = { viewModel.startSearching() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.nearby_refresh_cd))
                        }
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
                        text = { Text(stringResource(R.string.nearby_list_tab)) },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.nearby_map_tab)) },
                        icon = { Icon(Icons.Filled.Map, contentDescription = null) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !permissionGranted -> {
                        Text(
                            text = stringResource(R.string.nearby_permission_hint),
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
                            text = stringResource(R.string.nearby_location_error),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    else -> {
                        val state = uiState as NearbyUiState.Content
                        // НОВОЕ (баг 13): сводка поиска — сколько найдено, в каком радиусе,
                        // когда обновлено и с какой точностью геолокации.
                        NearbySearchSummary(state)
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (selectedTab == 0) {
                                NearbyPeopleList(
                                    people = state.people,
                                    myLat = state.myLat,
                                    myLng = state.myLng,
                                    onPersonClick = onPersonClick
                                )
                            } else {
                                NearbyPeopleMap(
                                    myLat = state.myLat,
                                    myLng = state.myLng,
                                    myAccuracyMeters = state.myAccuracyMeters,
                                    people = state.people,
                                    youHereLabel = stringResource(R.string.nearby_you_here)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * НОВОЕ (баг 13): компактная сводка над списком/картой — «Найдено: N · Радиус: 10 км ·
 * Обновлено: 14:32 · Точность: ±12 м». Дает мгновенное понимание, что искали, где и когда.
 */
@Composable
private fun NearbySearchSummary(state: NearbyUiState.Content) {
    val updatedAt = remember(state.searchedAtMillis) {
        if (state.searchedAtMillis > 0) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.searchedAtMillis))
        } else ""
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.People,
                contentDescription = null,
                tint = YodoPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buildString {
                    append(stringResource(R.string.nearby_found_count, state.people.size))
                    append(" · ")
                    append(stringResource(R.string.nearby_radius_km, "%.0f".format(state.radiusKm)))
                    if (updatedAt.isNotEmpty()) {
                        append(" · ")
                        append(stringResource(R.string.nearby_updated_at, updatedAt))
                    }
                    if (state.myAccuracyMeters > 0f) {
                        append(" · ")
                        append(stringResource(R.string.nearby_accuracy_m, state.myAccuracyMeters.toInt()))
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun NearbyPeopleList(
    people: List<NearbyPerson>,
    myLat: Double,
    myLng: Double,
    onPersonClick: (String) -> Unit
) {
    if (people.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.nearby_empty),
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
                    // НОВОЕ (баг 13): расстояние + направление («420 м · СВ») — сразу понятно,
                    // в какую сторону идти, не открывая карту.
                    Text(
                        text = formatDistance(person.distanceMeters) + " · " +
                            bearingToDirection(myLat, myLng, person.latitude, person.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * НОВОЕ (баг 13): карта стала информативнее:
 *  - круг точности геолокации вокруг своей позиции (радиус = accuracy);
 *  - масштаб автоматически подгоняется так, чтобы были видны все найденные люди;
 *  - у маркеров людей в описании — расстояние и направление;
 *  - кнопки зума и «где я» прямо на карте;
 *  - при обновлении поиска маркеры пересобираются (раньше карта строилась один раз
 *    в factory и при refresh оставалась со старыми данными).
 */
@Composable
private fun NearbyPeopleMap(
    myLat: Double,
    myLng: Double,
    myAccuracyMeters: Float,
    people: List<NearbyPerson>,
    youHereLabel: String
) {
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    mapRef.value = this
                    renderNearbyOverlays(myLat, myLng, myAccuracyMeters, people, youHereLabel)
                }
            },
            update = { map ->
                map.renderNearbyOverlays(myLat, myLng, myAccuracyMeters, people, youHereLabel)
            }
        )

        // Кнопки управления картой: зум и «где я»
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MapControlButton(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.nearby_zoom_in_cd)
            ) { mapRef.value?.controller?.zoomIn() }
            Spacer(modifier = Modifier.height(8.dp))
            MapControlButton(
                icon = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.nearby_zoom_out_cd)
            ) { mapRef.value?.controller?.zoomOut() }
            Spacer(modifier = Modifier.height(8.dp))
            MapControlButton(
                icon = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.nearby_my_location_cd)
            ) {
                mapRef.value?.controller?.animateTo(GeoPoint(myLat, myLng))
            }
        }
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/** Пересобирает все оверлеи карты: круг точности, свою метку и метки людей, затем подгоняет масштаб. */
private fun MapView.renderNearbyOverlays(
    myLat: Double,
    myLng: Double,
    myAccuracyMeters: Float,
    people: List<NearbyPerson>,
    youHereLabel: String
) {
    val me = GeoPoint(myLat, myLng)
    overlays.clear()

    // Круг точности геолокации — видно, насколько можно доверять своей позиции
    if (myAccuracyMeters > 0f) {
        val accuracyCircle = Polygon(this)
        accuracyCircle.points = Polygon.pointsAsCircle(me, myAccuracyMeters.toDouble())
        accuracyCircle.fillPaint.color = 0x3318A0FB
        accuracyCircle.fillPaint.style = Paint.Style.FILL
        accuracyCircle.outlinePaint.color = 0x6618A0FB
        accuracyCircle.outlinePaint.strokeWidth = 1.5f
        overlays.add(accuracyCircle)
    }

    // Своя метка
    val myMarker = Marker(this)
    myMarker.position = me
    myMarker.title = youHereLabel
    if (myAccuracyMeters > 0f) {
        myMarker.snippet = "±${myAccuracyMeters.toInt()} м"
    }
    overlays.add(myMarker)

    // Метки найденных людей: имя + расстояние + направление
    people.forEach { person ->
        val marker = Marker(this)
        marker.position = GeoPoint(person.latitude, person.longitude)
        marker.title = person.displayName
        marker.snippet = formatDistance(person.distanceMeters) + " · " +
            bearingToDirection(myLat, myLng, person.latitude, person.longitude)
        overlays.add(marker)
    }

    // Автоподбор масштаба: видно всех найденных людей и себя; никого нет — зум поближе к себе
    if (people.isNotEmpty()) {
        val points = ArrayList<GeoPoint>(people.size + 1)
        points.add(me)
        people.forEach { points.add(GeoPoint(it.latitude, it.longitude)) }
        zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false)
        // Если все точки практически совпадают, osmdroid может зазумиться слишком близко
        if (zoomLevelDouble > 17.0) controller.setZoom(17.0)
    } else {
        controller.setZoom(15.0)
        controller.setCenter(me)
    }
    invalidate()
}

private fun formatDistance(meters: Double): String {
    return if (meters < 1000) {
        "${meters.toInt()} м"
    } else {
        "%.1f км".format(meters / 1000)
    }
}

/** Направление от меня к человеку по 8 румбам: С, СВ, В, ЮВ, Ю, ЮЗ, З, СЗ. */
private fun bearingToDirection(myLat: Double, myLng: Double, lat: Double, lng: Double): String {
    val toRad = Math.PI / 180.0
    val y = Math.sin((lng - myLng) * toRad) * Math.cos(lat * toRad)
    val x = Math.cos(myLat * toRad) * Math.sin(lat * toRad) -
        Math.sin(myLat * toRad) * Math.cos(lat * toRad) * Math.cos((lng - myLng) * toRad)
    val bearing = (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
    val directions = arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
    return directions[(Math.round(bearing / 45.0) % 8).toInt()]
}

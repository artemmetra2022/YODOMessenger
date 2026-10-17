package app.yodo.messenger.features.nearby

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.yodo.messenger.domain.model.NearbyPerson
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
internal fun NearbyPeopleMap(state: NearbyUiState.Content, onPersonClick: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    // Use app colors, not system theme: Yodo supports an explicit theme preference.
    val dark = colors.surface.luminance() < 0.3f
    val accent = colors.primary.toArgb()
    val markerFill = colors.surface.toArgb()
    val ink = colors.onSurface.toArgb()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = state.people.firstOrNull { it.uid == selectedId }
    val currentState by rememberUpdatedState(state)
    val map = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMinZoomLevel(3.0)
            setMaxZoomLevel(19.0)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(state.myLat, state.myLng))
        }
    }
    var initialFitDone by remember(map) { mutableStateOf(false) }
    DisposableEffect(map, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!initialFitDone && map.width > 0 && map.height > 0) {
                map.fitPeople(currentState, false)
                initialFitDone = true
            }
        }
        map.addOnLayoutChangeListener(layoutListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose {
            map.removeOnLayoutChangeListener(layoutListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    LaunchedEffect(state.people) {
        if (selectedId != null && selected == null) selectedId = null
    }
    LaunchedEffect(map, state.myLat, state.myLng, state.myAccuracyMeters, state.people, accent, markerFill, ink, dark) {
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) {
            // Muted dark tiles, preserving street labels/contrast. Markers are not filtered.
            ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -.24f, -.48f, -.08f, 0f, 224f,
                -.24f, -.48f, -.08f, 0f, 230f,
                -.24f, -.48f, -.08f, 0f, 238f,
                0f, 0f, 0f, 1f, 0f
            )))
        } else null)
        map.renderPeople(state, accent, markerFill, ink) { selectedId = it }
        // Fit only on first layout. Refreshes and selecting a person never reset the camera.
        map.post {
            if (!initialFitDone && map.width > 0 && map.height > 0) {
                map.fitPeople(currentState, false)
                initialFitDone = true
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            Surface(
                onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") },
                color = colors.surface.copy(alpha = .96f), contentColor = colors.onSurface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            ) {
                Text("© OpenStreetMap", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            }
            val compact = maxHeight < 260.dp
            @Composable fun controls() {
                MapControl(Icons.Default.Add, "Приблизить") { map.controller.zoomIn() }
                MapControl(Icons.Default.Remove, "Отдалить") { map.controller.zoomOut() }
                MapControl(Icons.Default.CenterFocusStrong, "Показать всех") { map.fitPeople(currentState, true) }
                MapControl(Icons.Default.MyLocation, "Моё местоположение", primary = true) {
                    map.controller.animateTo(GeoPoint(currentState.myLat, currentState.myLng))
                }
            }
            if (compact) {
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { controls() }
            } else {
                Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
        }
        if (selected != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NearbyPersonCard(selected, state.myLat, state.myLng, Modifier.weight(1f)) { onPersonClick(selected.uid) }
                IconButton(onClick = { selectedId = null }) { Icon(Icons.Default.Close, "Закрыть карточку") }
            }
        } else {
            Text(if (state.people.isEmpty()) "Пока никого рядом · попробуйте обновить поиск"
                else "Коснитесь метки, чтобы открыть карточку",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun MapControl(icon: ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icon, label, Modifier.size(22.dp)) }
    }
}

private fun MapView.fitPeople(state: NearbyUiState.Content, animated: Boolean) {
    if (width <= 0 || height <= 0) return
    val points = listOf(GeoPoint(state.myLat, state.myLng)) + state.people
        .filter { validNearbyCoordinates(it.latitude, it.longitude) }
        .map { GeoPoint(it.latitude, it.longitude) }
    if (points.size == 1) {
        if (animated) controller.animateTo(points.first(), 15.0, 300L)
        else { controller.setZoom(15.0); controller.setCenter(points.first()) }
    } else {
        val padding = (64 * resources.displayMetrics.density).toInt().coerceAtMost(minOf(width, height) / 4)
        zoomToBoundingBox(BoundingBox.fromGeoPoints(points), animated, padding, 17.0, 300L)
    }
}

private fun MapView.renderPeople(state: NearbyUiState.Content, accent: Int, surface: Int, ink: Int, onSelect: (String) -> Unit) {
    overlays.forEach { if (it is Marker) it.closeInfoWindow() }
    overlays.clear()
    val me = GeoPoint(state.myLat, state.myLng)
    if (state.myAccuracyMeters.isFinite() && state.myAccuracyMeters > 0) {
        overlays.add(Polygon(this).apply {
            points = Polygon.pointsAsCircle(me, state.myAccuracyMeters.toDouble())
            fillPaint.color = (accent and 0x00ffffff) or 0x22000000
            outlinePaint.color = (accent and 0x00ffffff) or 0x66000000
            outlinePaint.strokeWidth = resources.displayMetrics.density
        })
    }
    overlays.add(Marker(this).apply {
        position = me
        title = "Вы здесь"
        icon = personMarker("", accent, android.graphics.Color.WHITE, true)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    })
    state.people.filter { validNearbyCoordinates(it.latitude, it.longitude) }.forEach { person ->
        overlays.add(Marker(this).apply {
            position = GeoPoint(person.latitude, person.longitude)
            title = person.displayName
            snippet = formatNearbyDistance(person.distanceMeters)
            icon = personMarker(nearbyInitials(person.displayName), surface, ink, false, accent)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> onSelect(person.uid); true }
        })
    }
    invalidate()
}

private fun MapView.personMarker(label: String, fill: Int, ink: Int, me: Boolean, border: Int = fill): BitmapDrawable {
    val d = resources.displayMetrics.density
    val size = (if (me) 32 * d else 48 * d).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    p.color = border
    canvas.drawCircle(center, center, center - d, p)
    p.color = fill
    canvas.drawCircle(center, center, center - 4 * d, p)
    p.color = ink
    if (me) canvas.drawCircle(center, center, 4 * d, p)
    else {
        p.textAlign = Paint.Align.CENTER
        p.textSize = 15 * d
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(label, center, center - (p.ascent() + p.descent()) / 2, p)
    }
    return BitmapDrawable(resources, bitmap)
}

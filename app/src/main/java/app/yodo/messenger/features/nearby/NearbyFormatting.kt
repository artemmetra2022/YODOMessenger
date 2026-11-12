package app.yodo.messenger.features.nearby

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun formatNearbyDistance(meters: Double): String = when {
    !meters.isFinite() || meters < 0 -> "—"
    meters < 1000 -> "${meters.toInt()} м"
    else -> String.format(Locale.getDefault(), "%.1f км", meters / 1000)
}

internal fun nearbyDirection(myLat: Double, myLng: Double, lat: Double, lng: Double): String {
    if (!validNearbyCoordinates(myLat, myLng) || !validNearbyCoordinates(lat, lng)) return "—"
    if (myLat == lat && myLng == lng) return "Рядом"
    val toRad = Math.PI / 180
    val y = sin((lng - myLng) * toRad) * cos(lat * toRad)
    val x = cos(myLat * toRad) * sin(lat * toRad) -
        sin(myLat * toRad) * cos(lat * toRad) * cos((lng - myLng) * toRad)
    val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
    return arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")[(bearing / 45).roundToInt() % 8]
}

internal fun validNearbyCoordinates(lat: Double, lng: Double): Boolean =
    lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

internal fun nearbyInitials(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }.take(2).map { it.codePointAt(0) }
    .joinToString("") { String(Character.toChars(it)) }.uppercase(Locale.getDefault()).ifBlank { "?" }

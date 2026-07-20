package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.NearbyPerson
import app.yodo.messenger.domain.repository.NearbyPeopleRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Геопозиция хранится в "users/{uid}": { latitude, longitude, locationUpdatedAt }.
 * Никакого специального гео-индекса Firestore не используется — при небольшом масштабе
 * приложения проще и надёжнее взять всех "недавно активных по геолокации" одним запросом
 * и посчитать точное расстояние (Haversine) на телефоне.
 */
@Singleton
class NearbyPeopleRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : NearbyPeopleRepository {

    companion object {
        private const val ACTIVE_WINDOW_MILLIS = 15 * 60 * 1000L // 15 минут
    }

    override suspend fun updateMyLocation(latitude: Double, longitude: Double) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid).update(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "locationUpdatedAt" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            // Не критично — просто не обновится в этот раз, следующая попытка перезапишет
        }
    }

    override suspend fun clearMyLocation() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid).update(
                mapOf(
                    "latitude" to FieldValue.delete(),
                    "longitude" to FieldValue.delete(),
                    "locationUpdatedAt" to FieldValue.delete()
                )
            ).await()
        } catch (e: Exception) {
            // Не критично
        }
    }

    override suspend fun findNearbyPeople(myLat: Double, myLng: Double, radiusKm: Double): List<NearbyPerson> {
        val uid = firebaseAuth.currentUser?.uid

        return try {
            val cutoff = System.currentTimeMillis() - ACTIVE_WINDOW_MILLIS
            val snapshot = firestore.collection("users")
                .whereGreaterThan("locationUpdatedAt", cutoff)
                .get().await()

            snapshot.documents.mapNotNull { doc ->
                if (doc.id == uid) return@mapNotNull null

                val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                val lng = doc.getDouble("longitude") ?: return@mapNotNull null
                val distance = haversineMeters(myLat, myLng, lat, lng)

                if (distance > radiusKm * 1000) return@mapNotNull null

                NearbyPerson(
                    uid = doc.id,
                    displayName = doc.getString("displayName") ?: "Пользователь",
                    photoUrl = doc.getString("avatarUrl"),
                    avatarBase64 = doc.getString("avatarBase64"),
                    latitude = lat,
                    longitude = lng,
                    distanceMeters = distance
                )
            }.sortedBy { it.distanceMeters }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}

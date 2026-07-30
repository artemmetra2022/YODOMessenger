package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.NearbyPerson

interface NearbyPeopleRepository {
    /** Публикует текущую геопозицию в свой профиль — виден только тем, кто ищет "рядом". */
    suspend fun updateMyLocation(latitude: Double, longitude: Double)

    /** Убирает геопозицию из профиля (например, при выходе с экрана "Рядом"). */
    suspend fun clearMyLocation()

    /**
     * Ищет людей, обновлявших геопозицию за последние 15 минут, в радиусе [radiusKm] от [myLat]/[myLng].
     * Расстояние считается на устройстве (формула Haversine) — без сторонних гео-индексов Firestore.
     */
    suspend fun findNearbyPeople(myLat: Double, myLng: Double, radiusKm: Double): List<NearbyPerson>
}

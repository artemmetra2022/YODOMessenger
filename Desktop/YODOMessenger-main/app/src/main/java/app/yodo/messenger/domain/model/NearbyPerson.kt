package app.yodo.messenger.domain.model

data class NearbyPerson(
    val uid: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarBase64: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double
)

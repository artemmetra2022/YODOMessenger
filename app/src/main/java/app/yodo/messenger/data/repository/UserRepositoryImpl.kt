package app.yodo.messenger.data.repository

import android.graphics.Bitmap
import android.net.Uri
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import app.yodo.messenger.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: android.content.Context
) : UserRepository {

    override fun observeCurrentUser(): Flow<YodoUser?> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(null); close(); return@callbackFlow }
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(YodoUser(
                        uid = uid,
                        displayName = firebaseAuth.currentUser?.displayName.orEmpty(),
                        email = firebaseAuth.currentUser?.email,
                        phoneNumber = firebaseAuth.currentUser?.phoneNumber,
                        photoUrl = firebaseAuth.currentUser?.photoUrl?.toString()
                    ))
                    return@addSnapshotListener
                }
                trySend(snapshot.toYodoUser(uid))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateDisplayName(name: String): ProfileUpdateResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ProfileUpdateResult.Error("Имя не может быть пустым")
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()).await()
            firestore.collection("users").document(user.uid)
                .update(mapOf("displayName" to trimmed, "displayNameLowercase" to trimmed.lowercase())).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить имя") }
    }

    override suspend fun updateBio(bio: String): ProfileUpdateResult {
        val trimmed = bio.trim().take(150)
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update("bio", trimmed).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить описание") }
    }

    override suspend fun updateUsername(username: String): ProfileUpdateResult {
        val normalized = username.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return ProfileUpdateResult.Error("Введите username")
        if (!normalized.matches(Regex("^[a-z0-9_]{3,20}$")))
            return ProfileUpdateResult.Error("Username: 3-20 символов, только латиница, цифры и \"_\"")
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.runTransaction { transaction ->
                val usernameRef = firestore.collection("usernames").document(normalized)
                val usernameSnapshot = transaction.get(usernameRef)
                if (usernameSnapshot.exists() && usernameSnapshot.getString("uid") != user.uid)
                    throw IllegalStateException("USERNAME_TAKEN")
                val userRef = firestore.collection("users").document(user.uid)
                val userSnapshot = transaction.get(userRef)
                val oldUsername = userSnapshot.getString("usernameLowercase")
                if (oldUsername != null && oldUsername != normalized)
                    transaction.delete(firestore.collection("usernames").document(oldUsername))
                transaction.set(usernameRef, mapOf("uid" to user.uid))
                transaction.update(userRef, mapOf("username" to normalized, "usernameLowercase" to normalized))
            }.await()
            ProfileUpdateResult.Success
        } catch (e: Exception) {
            val message = if (e.message?.contains("USERNAME_TAKEN") == true) "Этот username уже занят"
            else e.message ?: "Не удалось сохранить username"
            ProfileUpdateResult.Error(message)
        }
    }

    override suspend fun uploadAvatar(imageUri: Uri): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(context, imageUri)
            } ?: return ProfileUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("users").document(user.uid)
                .update(mapOf("avatarBase64" to base64, "avatarUrl" to null)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось загрузить фото") }
    }

    override suspend fun uploadAvatar(bitmap: Bitmap): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(bitmap)
            } ?: return ProfileUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("users").document(user.uid)
                .update(mapOf("avatarBase64" to base64, "avatarUrl" to null)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось загрузить фото") }
    }

    override suspend fun searchUsers(query: String): List<YodoUser> {
        val normalized = query.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return emptyList()
        val currentUid = firebaseAuth.currentUser?.uid
        val usersRef = firestore.collection("users")
        return try {
            val byName = usersRef.orderBy("displayNameLowercase")
                .startAt(normalized).endAt(normalized + "\uf8ff").limit(20).get().await()
            val byUsername = usersRef.orderBy("usernameLowercase")
                .startAt(normalized).endAt(normalized + "\uf8ff").limit(20).get().await()
            (byName.documents + byUsername.documents)
                .distinctBy { it.id }.filter { it.id != currentUid }
                .map { it.toYodoUser(it.id) }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getUserById(uid: String): YodoUser? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) doc.toYodoUser(uid) else null
        } catch (e: Exception) { null }
    }

    override suspend fun updateAboutMe(aboutMe: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("aboutMe", aboutMe.trim().take(300)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить «О себе»") }
    }

    override suspend fun updateBirthDate(birthDate: String): ProfileUpdateResult {
        val trimmed = birthDate.trim()
        // Пустая строка — это "очистить дату рождения", разрешаем без проверки формата.
        if (trimmed.isNotEmpty()) {
            val validationError = app.yodo.messenger.util.BirthDateValidator.validate(trimmed)
            if (validationError != null) return ProfileUpdateResult.Error(validationError)
        }
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("birthDate", trimmed).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить дату рождения") }
    }

    override suspend fun updateLocation(location: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("location", location.trim().take(100)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить местоположение") }
    }

    override suspend fun updateWebsite(website: String): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid)
                .update("website", website.trim().take(200)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить сайт") }
    }

    override suspend fun updatePrivacySettings(
        showBirthDate: Boolean, showAboutMe: Boolean, showLocation: Boolean,
        showWebsite: Boolean, showPhoneNumber: Boolean, showEmail: Boolean
    ): ProfileUpdateResult {
        val user = firebaseAuth.currentUser ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(user.uid).update(mapOf(
                "showBirthDate" to showBirthDate, "showAboutMe" to showAboutMe,
                "showLocation" to showLocation, "showWebsite" to showWebsite,
                "showPhoneNumber" to showPhoneNumber, "showEmail" to showEmail
            )).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось обновить настройки") }
    }

    override suspend fun blockUser(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(me)
                .update("blockedUsers", FieldValue.arrayUnion(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось заблокировать") }
    }

    override suspend fun unblockUser(uid: String): ProfileUpdateResult {
        val me = firebaseAuth.currentUser?.uid ?: return ProfileUpdateResult.Error("Вы не авторизованы")
        return try {
            firestore.collection("users").document(me)
                .update("blockedUsers", FieldValue.arrayRemove(uid)).await()
            ProfileUpdateResult.Success
        } catch (e: Exception) { ProfileUpdateResult.Error(e.message ?: "Не удалось разблокировать") }
    }

    override suspend fun getBlockedUsers(): List<YodoUser> {
        val me = firebaseAuth.currentUser?.uid ?: return emptyList()
        return try {
            val myDoc = firestore.collection("users").document(me).get().await()
            val blockedIds = (myDoc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            blockedIds.mapNotNull { getUserById(it) }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun isUserBlocked(uid: String): Boolean {
        val me = firebaseAuth.currentUser?.uid ?: return false
        return try {
            val myDoc = firestore.collection("users").document(me).get().await()
            val blockedIds = (myDoc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            uid in blockedIds
        } catch (e: Exception) { false }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toYodoUser(uid: String) = YodoUser(
        uid = uid,
        displayName = getString("displayName") ?: "",
        username = getString("username"),
        bio = getString("bio"),
        email = getString("email"),
        phoneNumber = getString("phoneNumber"),
        photoUrl = getString("avatarUrl"),
        avatarBase64 = getString("avatarBase64"),
        aboutMe = getString("aboutMe"),
        birthDate = getString("birthDate"),
        location = getString("location"),
        website = getString("website"),
        showBirthDate = getBoolean("showBirthDate") ?: true,
        showAboutMe = getBoolean("showAboutMe") ?: true,
        showLocation = getBoolean("showLocation") ?: true,
        showWebsite = getBoolean("showWebsite") ?: true,
        showPhoneNumber = getBoolean("showPhoneNumber") ?: false,
        showEmail = getBoolean("showEmail") ?: false
    )
}

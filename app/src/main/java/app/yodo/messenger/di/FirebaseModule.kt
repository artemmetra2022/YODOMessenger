package app.yodo.messenger.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * ИСПРАВЛЕНО (AN/AO): раньше Firestore создавался без явных настроек, и при
     * открытии чата приложение ждало ответ сервера — отсюда «сообщений пока нет»
     * и появление всего через ~30 секунд на медленной сети.
     *
     * Теперь включён постоянный офлайн-кэш без ограничения размера: список чатов и
     * сообщения показываются МГНОВЕННО из кэша, а свежие данные доезжают фоном.
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        try {
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()
        } catch (_: Exception) {
            // Настройки можно менять только до первого обращения к Firestore — если опоздали,
            // просто работаем с настройками по умолчанию (кэш всё равно включён).
        }
        return firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()
}

# =============================================================================
# R8 keep-правила для release-сборки (minifyEnabled = true).
# Правила для AndroidX/Compose/Firebase/Room/Hilt поставляются самими
# библиотеками (consumer rules) — здесь только то, чего им не хватает.
# =============================================================================

# Атрибуты, без которых ломаются рефлексия и сериализация:
# Signature — дженерики Retrofit-сервисов, *Annotation* + RuntimeVisibleAnnotations —
# аннотации @Serializable/@SerialName, InnerClasses/EnclosingMethod — вложенные
# классы сериализаторов.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, AnnotationDefault

# --- kotlinx-serialization (Retrofit-конвертер, ChatFolder) ---
# Сериализатор ищется через Companion.serializer(...) рефлексивно.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.yodo.messenger.**$$serializer { *; }
-keepclassmembers class app.yodo.messenger.** {
    *** Companion;
}
-keepclasseswithmembers class app.yodo.messenger.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.AnnotationsKt

# --- Google Tink (сквозное шифрование) ---
# Реестр ключей (Registry) резолвит KeyManager'ы по имени класса через рефлексию.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Retrofit / OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Guava (ListenableFuture от camera-lifecycle) ---
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn jsr305.**
-dontwarn org.checkerframework.**

# --- osmdroid (карта OSM) ---
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# --- ZXing (генерация QR) ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- Google Play Services (Nearby, Location), Firebase, Credential Manager ---
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# Транзитивные Java-зависимости, не используемые на Android.
-dontwarn org.slf4j.**
-dontwarn org.joda.time.**

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // С Kotlin 2.0+ Compose Compiler больше не встроен в Kotlin Gradle plugin —
    // нужен отдельный плагин вместо composeOptions.kotlinCompilerExtensionVersion.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Известный баг Hilt+KSP (google/dagger#4161): когда в модуле есть ещё один KSP-процессор,
// вызывающий многораундовую обработку (у нас это Room), Hilt при aggregating task пытается
// пересоздать уже сгенерированные файлы (hilt_aggregated_deps/...) и падает с
// FileAlreadyExistsException. Отключение aggregating task — официально задокументированный
// обходной путь для этого случая.
hilt {
    enableAggregatingTask = false
}

android {
    namespace = "app.yodo.messenger"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.yodo.messenger"
        minSdk = 26
        targetSdk = 34
        // Версия подтягивается из последнего GitHub-релиза через CI (флаги -PversionName/-PversionCode).
        // Локальные сборки без флагов используют дефолтные значения.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-compiler:2.58")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room (local DB) — 2.7.0+ добавляет полноценную поддержку KSP2 (2.6.1 её не имел,
    // что дополнительно повышало риск конфликтов между Room- и Hilt-процессорами в KSP).
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // QR-код — только ядро ZXing (без Android Embedded, рисуем на Canvas сами)
    implementation("com.google.zxing:core:3.5.3")

    // НОВОЕ (сквозное шифрование): Google Tink — гибридное шифрование HPKE (X25519 + AES-256-GCM).
    implementation("com.google.crypto.tink:tink-android:1.13.0")

    // НОВОЕ (офлайн обмен контактами по QR): CameraX для встроенного сканера QR-кодов.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // НОВОЕ (офлайн обмен контактами по QR): полноценный Guava, а не только пустышка
    // listenablefuture:1.0 — иначе Kotlin не может разрешить тип ListenableFuture,
    // который возвращает ProcessCameraProvider.getInstance(...) из camera-lifecycle.
    implementation("com.google.guava:guava:31.0.1-android")

    // Firebase (Auth / FCM / Storage) — временный backend до готовности своего сервера
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")

    // Google Sign-In (через Credential Manager — актуальный способ, не deprecated GoogleSignInClient)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Nearby Connections — офлайн P2P-обмен сообщениями (Bluetooth/Wi-Fi Direct, без интернета)
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // "Кто рядом" — геолокация + карта. OpenStreetMap вместо Google Maps: последний требует
    // привязку карты оплаты для Maps SDK даже на бесплатном тарифе — тот же блокер, что был
    // с Firebase Storage/Blaze. osmdroid — полностью бесплатен, без API-ключа и биллинга.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

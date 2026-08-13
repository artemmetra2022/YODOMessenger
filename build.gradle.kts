// Top-level build file
plugins {
    id("com.android.application") version "8.5.0" apply false
    // Kotlin/KSP/Hilt подняты до актуальной связки: связка Kotlin 1.9.24/2.0.21 + Hilt 2.5x
    // регулярно ловит баги KSP при валидации Dagger-биндингов в модулях, где рядом с Hilt
    // работает ещё один KSP-процессор (у нас — Room): см. google/ksp#1855, google/dagger#4161.
    // Kotlin 2.2.20 + KSP 2.2.20-2.0.3 — актуальная стабильная связка, где эти баги устранены.
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.3" apply false
}

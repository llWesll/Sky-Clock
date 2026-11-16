plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.skyoverlay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skyoverlay.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { buildConfig = true }

    // Tres versoes do app, que podem ficar instaladas juntas:
    //  completo  = eventos (Geyser/Vovo) + alerta de fragmento (com chaves para ligar/desligar)
    //  eventos   = so os horarios dos eventos
    //  fragmento = so o alerta de fragmento
    flavorDimensions += "conteudo"
    productFlavors {
        create("completo") {
            dimension = "conteudo"
            buildConfigField("boolean", "HAS_EVENTS", "true")
            buildConfigField("boolean", "HAS_SHARDS", "true")
            manifestPlaceholders["appLabel"] = "Sky Overlay"
        }
        create("eventos") {
            dimension = "conteudo"
            applicationIdSuffix = ".eventos"
            buildConfigField("boolean", "HAS_EVENTS", "true")
            buildConfigField("boolean", "HAS_SHARDS", "false")
            manifestPlaceholders["appLabel"] = "Sky Eventos"
        }
        create("fragmento") {
            dimension = "conteudo"
            applicationIdSuffix = ".fragmento"
            buildConfigField("boolean", "HAS_EVENTS", "false")
            buildConfigField("boolean", "HAS_SHARDS", "true")
            manifestPlaceholders["appLabel"] = "Sky Fragmento"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

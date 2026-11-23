import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Secrets de signature chargés depuis keystore.properties (hors git).
// Absent (ex. clone sans secrets) -> build release non signé, sans planter.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.tstfaasaopwsw"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tstfaasaopwsw"
        minSdk = 24
        targetSdk = 34
        // En CI : ./gradlew assembleRelease -PversionCode=8 (dérivé du tag vN).
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 7
        versionName = ((project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 7).toString()

        // GeckoView embarque du natif par architecture. On ne garde que l'arm64 du
        // téléphone réel : l'APK release (téléchargé à chaque mise à jour) reste moitié
        // moins lourd. Le x86_64 de l'émulateur est ajouté au seul build debug ci-dessous.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += "x86_64" } // émulateur, debug seulement
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // expose BuildConfig.VERSION_CODE à l'updater
    }
    composeOptions {
        // Compilateur Compose compatible Kotlin 1.9.24.
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // UI : Jetpack Compose + Material 3 (Material You).
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Navigateur embarqué : GeckoView (moteur Firefox). Version ESR 128, compatible
    // compileSdk 34. C'est la seule base Android qui laisse l'app filtrer la navigation.
    implementation("org.mozilla.geckoview:geckoview:128.0.20240725162350")
}

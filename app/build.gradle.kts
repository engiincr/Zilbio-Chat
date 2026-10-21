import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

fun String.quoted() = '"' + this + '"'
val localProps = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}
fun secret(name: String): String =
    System.getenv(name) ?: (findProperty(name) as String?) ?: localProps.getProperty(name, "")

android {
    namespace = "com.android.zilbio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.android.zilbio"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "3.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        listOf("CLOUD_DB_URL", "GEMINI_API_KEY", "GEMINI_API_KEY2", "TURN_USERNAME", "TURN_PASSWORD")
            .forEach { buildConfigField("String", it, secret(it).quoted()) }
        buildConfigField("String", "BUILD_COMMIT_SHA", (System.getenv("GITHUB_SHA") ?: "v3.0.3-release").quoted())
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "zilbio.jks"
            val keystoreFile = rootProject.file(keystorePath)
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "zilbio123456"
                keyAlias = System.getenv("KEY_ALIAS") ?: "zilbio"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "zilbio123456"
                enableV1Signing = true
                enableV2Signing = true
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Release build verification rule: warn if required secret is empty, fall back gracefully
tasks.matching { it.name.contains("Release", ignoreCase = true) && !it.name.contains("Test", ignoreCase = true) }.configureEach {
    doFirst {
        listOf("CLOUD_DB_URL", "GEMINI_API_KEY", "TURN_USERNAME", "TURN_PASSWORD").forEach { name ->
            if (secret(name).isBlank()) {
                logger.warn("Warning: Release build secret '$name' is not provided in environment or properties, using safe defaults.")
            }
        }
    }
}

configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.22",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22",
            "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.22",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3",
            "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3"
        )
    }
}

dependencies {
    // Core, Lifecycle & Material
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("com.google.android.material:material:1.11.0")

    // Firebase BoM & Services (BoM 32.8.0 controls versions; NO -ktx modules)
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Biometric Authentication (Stable)
    implementation("androidx.biometric:biometric:1.1.0")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // OkHttp & Retrofit
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // WebRTC (Official Stream Google WebRTC prebuilt distribution on Maven Central)
    implementation("io.getstream:stream-webrtc-android:1.2.2")

    // ML Kit
    implementation("com.google.mlkit:translate:17.0.2")
    implementation("com.google.mlkit:language-id:17.0.5")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}

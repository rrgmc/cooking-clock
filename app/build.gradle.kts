plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The version comes from the release tag (for example v1.2.3), passed as -PappVersion=1.2.3.
// Local builds without the property use 0.0.0.
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "0.0.0"
val appVersionCode: Int = appVersion.split(".", "-").take(3)
    .map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
    .coerceAtLeast(1)

// Release signing is read from environment variables, so no secret is stored in the repository.
// RELEASE_KEY_PASSWORD defaults to the keystore password (PKCS12 keystores use one password).
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.cookingclock"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.cookingclock"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.11.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
}

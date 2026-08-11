plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.androidscreentuner.extreme"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.androidscreentuner.extreme"
        minSdk = 26
        targetSdk = 34
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.1.0"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // sign separately with ephemeral keys
    // wait. isn't trust already solved by reproducible builds and hash?
    // but i guess Android PackageManager demands it for reasons
    // no use to overthink about this
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}

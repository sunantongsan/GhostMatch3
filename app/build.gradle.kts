plugins {
    id("com.android.application")
}

android {
    namespace = "com.sunantongsan.ghostmatch3"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sunantongsan.ghostmatch3"
        minSdk = 24
        targetSdk = 36
        versionCode = 29
        versionName = "0.29.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Artwork is already compressed; avoid AAPT2 re-crunching sprite sheets.
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Test-only AdMob integration. Replace sample identifiers only after privacy review.
dependencies {
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}

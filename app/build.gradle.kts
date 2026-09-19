plugins {
    id("com.android.application")
}

android {
    namespace = "com.sunantongsan.ghostmatch3"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sunantongsan.ghostmatch3"
        minSdk = 24
        targetSdk = 35
        versionCode = 28
        versionName = "0.28.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

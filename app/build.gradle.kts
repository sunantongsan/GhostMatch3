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
        versionCode = 4
        versionName = "0.4.0"
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

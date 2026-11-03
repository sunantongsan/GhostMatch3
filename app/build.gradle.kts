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
        manifestPlaceholders["appLabel"] = "ท่านเซ่อจ่อย"
        versionCode = 55
        versionName = "0.55.0"
        testInstrumentationRunner = "com.sunantongsan.ghostmatch3.SerJoySmoke"
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("boolean", "TEST_ADS", "true")
            buildConfigField("String", "REWARDED_AD_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        create("storyPreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".storypreview"
            manifestPlaceholders["appLabel"] = "เซ่อจ่อย • ฉากใหม่"
            matchingFallbacks += listOf("debug")
        }
        release {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-4585492700643566~4565298058"
            buildConfigField("boolean", "TEST_ADS", "false")
            buildConfigField("String", "REWARDED_AD_ID", "\"ca-app-pub-4585492700643566/1483984937\"")
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

// Release uses the verified Ghost/SerJoy AdMob identifiers; debug uses Google demo ads.
dependencies {
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}

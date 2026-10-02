plugins {
    id("com.android.application")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

android {
    namespace = "com.sentineldroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sentineldroid"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
        buildConfigField("boolean", "SNIPER_ENABLED", "false")
    }

    buildFeatures {
        buildConfig = true
        resValues = false
        shaders = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("sniper") {
            initWith(getByName("release"))
            buildConfigField("boolean", "SNIPER_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

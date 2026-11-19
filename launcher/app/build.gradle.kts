plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.domivka.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.domivka.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
    }

    buildTypes {
        release {
            // Smaller / faster build; signed with the debug key so it can be sideloaded as-is.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
    }
}

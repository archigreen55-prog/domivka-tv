plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.domivka.remote"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.domivka.remote"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES") }
    lint { abortOnError = false }
}
dependencies {
    implementation("dev.mobile:dadb:1.2.10")
}

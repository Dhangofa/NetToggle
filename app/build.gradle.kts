plugins {
    id("com.android.application")
}

android {
    namespace = "com.dhangofa.networktoggle"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dhangofa.networktoggle"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Shizuku API
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    
    // Aligned with Shizuku's strict version requirement to satisfy Gradle compiler
    compileOnly("androidx.annotation:annotation:1.3.0")
}

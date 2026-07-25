plugins {
    id("com.android.application")
}

android {
    namespace = "com.dhangofa.networktoggle"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dhangofa.networktoggle"
        minSdk = 24
        targetSdk = 34
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
    
    // Required to prevent Shizuku RestrictTo$Scope compilation errors
    compileOnly("androidx.annotation:annotation:1.6.0")
}

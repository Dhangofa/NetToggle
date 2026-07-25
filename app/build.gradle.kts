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
}

dependencies {
    // Left completely empty to keep the app minimal and pure Java
}

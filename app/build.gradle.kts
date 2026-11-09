plugins {
    id("com.android.application")
}

android {
    namespace = "com.aanext.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aanext.app"
        // Confirmed head unit: alps YT5760B / AC8257, API 28.
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

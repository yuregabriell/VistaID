plugins { id("com.android.application") }

android {
    namespace = "com.vista.id"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vista.id"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-local"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

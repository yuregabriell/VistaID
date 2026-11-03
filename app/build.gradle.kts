plugins { id("com.android.application") }

val vistaSupabaseUrl = providers.gradleProperty("VISTA_SUPABASE_URL").orNull
    ?: System.getenv("VISTA_SUPABASE_URL") ?: "https://hleekwlptmisfvamgext.supabase.co"
val vistaSupabaseKey = providers.gradleProperty("VISTA_SUPABASE_PUBLISHABLE_KEY").orNull
    ?: System.getenv("VISTA_SUPABASE_PUBLISHABLE_KEY") ?: "sb_publishable_7H5NVq8e6_6aNsgU8p7S-A_lBdiLspn"
fun javaLiteral(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.vista.id"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vista.id"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-supabase-preview"
        buildConfigField("String", "VISTA_SUPABASE_URL", javaLiteral(vistaSupabaseUrl))
        buildConfigField("String", "VISTA_SUPABASE_PUBLISHABLE_KEY", javaLiteral(vistaSupabaseKey))
    }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    // Camera QR scanner runs locally; no cloud OCR or login provider.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

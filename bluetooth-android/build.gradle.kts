plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "io.openhoyi.bluetooth"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { api(project(":device-session")) }

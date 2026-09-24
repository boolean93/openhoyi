plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "io.openhoyi.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.openhoyi.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "MOCK_MODE", "false") }
        getByName("release") { buildConfigField("boolean", "MOCK_MODE", "false") }
        create("mock") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".mock"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "MOCK_MODE", "true")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":bluetooth-android"))
    implementation(project(":trace-core"))
    testImplementation("junit:junit:4.13.2")
}

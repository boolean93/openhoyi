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
    testImplementation("org.json:json:20240303")
}

// JVM presentation tests read these staged catalogs/default templates directly from disk.
// Register them so changes cannot reuse an old passing Test result.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.files(rootProject.fileTree("localization") {
        include("catalog/*.json", "drafts/**/*.json")
    }).withPropertyName("languageCatalogs")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    inputs.files(fileTree("src/main/res") { include("values-*/strings.xml") })
        .withPropertyName("translatedStringTemplates")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    inputs.file(file("src/main/res/values/strings.xml"))
        .withPropertyName("defaultStringTemplates")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

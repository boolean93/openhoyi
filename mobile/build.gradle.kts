plugins { id("com.android.application"); kotlin("android") }
apply(from = "distribution.gradle.kts")
@Suppress("UNCHECKED_CAST")
val externalSigning = extra["hoyiExternalSigning"] as Map<String, String?>
val externalSigningComplete = extra["hoyiExternalSigningComplete"] as Boolean
android {
    namespace = "io.openhoyi.mobile"
    compileSdk = 35
    testBuildType = "mock"
    defaultConfig {
        applicationId = "io.openhoyi.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = extra["hoyiDistributionVersionCode"] as Int
        versionName = extra["hoyiDistributionVersionName"] as String
        testInstrumentationRunner = "io.openhoyi.mobile.BrewAudioInstrumentation"
    }
    buildFeatures { buildConfig = true }
    if (externalSigningComplete) {
        signingConfigs.create("externalRelease") {
            storeFile = file(externalSigning.getValue("STORE_FILE")!!)
            storePassword = externalSigning.getValue("STORE_PASSWORD")
            keyAlias = externalSigning.getValue("KEY_ALIAS")
            keyPassword = externalSigning.getValue("KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "MOCK_MODE", "false") }
        getByName("release") {
            buildConfigField("boolean", "MOCK_MODE", "false")
            if (externalSigningComplete) signingConfig = signingConfigs.getByName("externalRelease")
        }
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

// Direct asset/provenance readers must not reuse stale passing JVM results.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.files(fileTree("src/main/assets/brew-feedback"))
        .withPropertyName("brewFeedbackAssets")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("docs/evidence/local-brew-audio-assets.json"))
        .withPropertyName("brewFeedbackProvenance")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

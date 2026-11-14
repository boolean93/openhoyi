plugins { id("com.android.application"); kotlin("android") }
apply(from = "distribution.gradle.kts")
@Suppress("UNCHECKED_CAST")
val externalSigning = extra["hoyiExternalSigning"] as Map<String, String?>
val externalSigningComplete = extra["hoyiExternalSigningComplete"] as Boolean
val updateChecks = providers.gradleProperty("hoyiUpdateChecks").orNull?.let {
    when (it) { "true" -> true; "false" -> false; else -> throw GradleException("hoyiUpdateChecks must be true or false") }
} ?: false
android {
    namespace = "io.openhoyi.mobile"
    compileSdk = 35
    testBuildType = if (updateChecks) "updateFixture" else "mock"
    defaultConfig {
        applicationId = "io.openhoyi.mobile"
        minSdk = 26
        targetSdk = 35
        // Filter transitive AndroidX locales as well as application translations.
        resourceConfigurations += listOf("zh", "en")
        versionCode = extra["hoyiDistributionVersionCode"] as Int
        versionName = extra["hoyiDistributionVersionName"] as String
        testInstrumentationRunner = if (updateChecks) "io.openhoyi.mobile.UpdateAcceptanceInstrumentation" else "io.openhoyi.mobile.BrewAudioInstrumentation"
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
    if (updateChecks) {
        buildTypes.create("updateFixture") {
            initWith(buildTypes.getByName("mock"))
            applicationIdSuffix = ".updatefixture"
            matchingFallbacks += listOf("mock", "debug")
        }
        sourceSets.getByName("updateFixture") {
            java.srcDir("src/mock/kotlin")
            res.srcDir("src/mock/res")
        }
        sourceSets.getByName("androidTest") {
            manifest.srcFile("src/updateFixtureTest/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/updateFixtureTest/kotlin"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.fragment:fragment:1.8.6")
    implementation(project(":bean-core"))
    implementation(project(":bluetooth-android"))
    implementation(project(":trace-core"))
    implementation("com.android.tools.build:apksig:8.10.0")
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

// Exercise the actual runtime APK-signature verifier using tiny, locally generated signed bytes.
// Fixture keys are generated outside the repository and destroyed before this task returns.
val updateApkFixtureDirectory = layout.buildDirectory.dir("update-apk-fixtures")
val prepareUpdateApkFixtures = tasks.register<Exec>("prepareUpdateApkFixtures") {
    inputs.file(rootProject.file("scripts/prepare_update_apk_fixtures.py"))
    outputs.dir(updateApkFixtureDirectory)
    commandLine("python3", rootProject.file("scripts/prepare_update_apk_fixtures.py").absolutePath,
        "--sdk", android.sdkDirectory.absolutePath, "--output", updateApkFixtureDirectory.get().asFile.absolutePath)
}
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(prepareUpdateApkFixtures)
    systemProperty("hoyi.update.fixture.dir", updateApkFixtureDirectory.get().asFile.absolutePath)
}

if (updateChecks) {
    android.sourceSets.getByName("updateFixture").assets.srcDir(updateApkFixtureDirectory)
    tasks.matching { it.name == "mergeUpdateFixtureAssets" }.configureEach { dependsOn(prepareUpdateApkFixtures) }
}
if (updateChecks) {
    kotlin.sourceSets.getByName("androidTest").kotlin.setSrcDirs(listOf("src/updateFixtureTest/kotlin"))
}

// Source guards read text/XML, not only bytecode. Comments/resources must invalidate cached results too.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.files(fileTree("src/main/kotlin") { include("**/*.kt") })
        .withPropertyName("appTypographySourceGuard").withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    inputs.files(file("src/main/AndroidManifest.xml"), fileTree("src/main/res") { include("**/*.xml") })
        .withPropertyName("appTypographyResourceGuard").withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

// Prompt fixtures/text changes must invalidate their real-parser JVM contract checks.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.files(rootProject.fileTree("docs/curve-import"))
        .withPropertyName("curveImportPromptContract")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    systemProperty("hoyi.curve.prompt.dir", rootProject.file("docs/curve-import").absolutePath)
}

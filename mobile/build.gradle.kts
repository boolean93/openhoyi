import java.io.File as JavaIoFile
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey

plugins { id("com.android.application"); kotlin("android") }
// BEGIN VERBATIM DISTRIBUTION LOGIC: scope matches the former applied script.
run {

// Local developer defaults remain unchanged. Release requires explicit declarations.
val declaredCode = providers.gradleProperty("hoyiVersionCode").orNull
val declaredName = providers.gradleProperty("hoyiVersionName").orNull
val previousCode = providers.gradleProperty("hoyiPreviousVersionCode").orNull
val buildCode = declaredCode?.toIntOrNull() ?: if (declaredCode == null) 1 else 0
if (buildCode <= 0) throw GradleException("hoyiVersionCode must be a positive Android version code")
val buildName = declaredName ?: "0.1.0"
if (buildName.length > 64 || !buildName.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][0-9A-Za-z.-]+)?"))) {
    throw GradleException("hoyiVersionName must be a version identifier")
}
extra["hoyiDistributionVersionCode"] = buildCode
extra["hoyiDistributionVersionName"] = buildName

val externalSigning = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD", "CERT_SHA256")
    .associateWith { providers.environmentVariable("HOYI_RELEASE_$it").orNull }
val signingComplete = externalSigning.values.all { !it.isNullOrEmpty() }
extra["hoyiExternalSigning"] = externalSigning
extra["hoyiExternalSigningComplete"] = signingComplete

val verifyReleaseDistribution = tasks.register("verifyReleaseDistribution") {
    group = "verification"
    description = "Require declared increasing release version and verified external signing identity"
    // Validate on every invocation; no cached success for changed keys or environment.
    doLast {
        if (declaredCode == null || declaredName == null || previousCode == null) {
            throw GradleException("Release builds require explicit version properties")
        }
        val previous = previousCode.toIntOrNull()
        if (previous == null || previous < 0 || buildCode <= previous) {
            throw GradleException("Release version must be greater than the declared previous version")
        }
        if (!signingComplete) throw GradleException("Release signing configuration is incomplete")
        val suppliedFile = JavaIoFile(externalSigning.getValue("STORE_FILE")!!)
        if (!suppliedFile.isAbsolute) throw GradleException("Release signing key path must be absolute")
        val keyFile = suppliedFile.canonicalFile
        if (keyFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) {
            throw GradleException("Release signing key must be outside the repository")
        }
        if (!keyFile.isFile) throw GradleException("Release signing key file is unavailable")
        val expected = externalSigning.getValue("CERT_SHA256")!!
        if (!expected.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw GradleException("Release signing certificate must be a SHA256 hex digest")
        }
        val storePassword = externalSigning.getValue("STORE_PASSWORD")!!.toCharArray()
        val keyPassword = externalSigning.getValue("KEY_PASSWORD")!!.toCharArray()
        val actual = try {
            val store = KeyStore.getInstance(keyFile, storePassword)
            val alias = externalSigning.getValue("KEY_ALIAS")!!
            if (store.getKey(alias, keyPassword) !is PrivateKey) throw IllegalStateException()
            val certificate = store.getCertificate(alias) ?: throw IllegalStateException()
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } catch (_: Exception) {
            // Do not include provider exceptions, paths, aliases, or passwords in errors.
            throw GradleException("Release signing keystore cannot be opened")
        } finally {
            storePassword.fill('\u0000')
            keyPassword.fill('\u0000')
        }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw GradleException("Release signing certificate does not match the expected identity")
        }
        logger.lifecycle("Release distribution configuration verified")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseDistribution)
}
}
// END VERBATIM DISTRIBUTION LOGIC
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
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
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

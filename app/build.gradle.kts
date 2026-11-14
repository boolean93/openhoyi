plugins { id("com.android.application"); kotlin("android") }
android {
    namespace="io.openhoyi.lab"
    buildFeatures { buildConfig = true }
    compileSdk=35
    defaultConfig {
        applicationId="io.openhoyi.lab"
        minSdk=26
        targetSdk=35
        versionCode=1
        versionName="0.1.0"
        testInstrumentationRunner="io.openhoyi.lab.LabSmokeInstrumentation"
    }
    compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":bluetooth-android"))
    implementation(project(":trace-core"))
    testImplementation("junit:junit:4.13.2")
}

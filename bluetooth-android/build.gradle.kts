plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "io.openhoyi.bluetooth"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { api(project(":device-session")); testImplementation("junit:junit:4.13.2") }

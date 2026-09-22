pluginManagement {
    repositories { mavenCentral(); google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google\\.testing.*") } }; gradlePluginPortal() }
    resolutionStrategy.eachPlugin {
        if (requested.id.id.startsWith("org.jetbrains.kotlin")) useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
        if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:${requested.version}")
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Optional repository mirror for networks unable to reach dl.google.com.
        if (providers.gradleProperty("googleMirror").orNull == "aliyun") {
            maven("https://maven.aliyun.com/repository/google") { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*") } }
        }
        google()
    }
}
rootProject.name = "openhoyi"
include(":protocol-core", ":device-session", ":bluetooth-android")

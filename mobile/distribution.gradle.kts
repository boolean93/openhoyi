import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey

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
        val suppliedFile = java.io.File(externalSigning.getValue("STORE_FILE")!!)
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

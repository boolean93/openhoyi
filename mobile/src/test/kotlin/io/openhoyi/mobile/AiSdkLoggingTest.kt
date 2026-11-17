package io.openhoyi.mobile

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.LoggerFactory

class AiSdkLoggingTest {
    @Test fun sdkLoggerNeverEvaluatesPrivatePayloadAtAnyLevel() {
        // This property would otherwise pick the real Android native backend before SLF4J.
        val property = "kotlin-logging-to-android-native"
        val old = System.getProperty(property)
        try {
            System.setProperty(property, "true")
            AiSdkLogging.install()
            val logger = KotlinLogging.logger("ai.koog.private.sentinel")
            for (level in Level.entries) {
                assertFalse(logger.isLoggingEnabledFor(level))
                logger.at(level) { error("PRIVATE_KEY_USER_TOOL_SENTINEL must never evaluate") }
            }
            logger.error(IllegalStateException("PRIVATE_THROWABLE_SENTINEL")) { error("must not evaluate") }
            assertEquals("org.slf4j.helpers.NOPLoggerFactory", LoggerFactory.getILoggerFactory().javaClass.name)
            assertEquals(listOf("org.slf4j.nop.NOPServiceProvider"),
                java.util.ServiceLoader.load(org.slf4j.spi.SLF4JServiceProvider::class.java).map { it.javaClass.name })
        } finally { if (old == null) System.clearProperty(property) else System.setProperty(property, old) }
    }
}

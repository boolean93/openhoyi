package io.openhoyi.mobile

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KLoggerFactory
import io.github.oshai.kotlinlogging.KLoggingEventBuilder
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.Marker

/** Installed before any Koog static logger. No payload lambda is evaluated on any backend. */
internal object AiSdkLogging {
    private object SilentFactory : KLoggerFactory {
        override fun logger(name: String): KLogger = object : KLogger {
            override val name = name
            override fun isLoggingEnabledFor(level: Level, marker: Marker?) = false
            override fun at(level: Level, marker: Marker?, block: KLoggingEventBuilder.() -> Unit) = Unit
        }
    }
    fun install() {
        KotlinLoggingConfiguration.logStartupMessage = false
        KotlinLoggingConfiguration.loggerFactory = SilentFactory
    }
}

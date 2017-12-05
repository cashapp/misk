package misk.logging

import mu.KLogger
import mu.KotlinLogging

inline fun <reified T> getLogger() : KLogger {
    return KotlinLogging.logger(T::class.qualifiedName!!)
}

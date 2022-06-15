package wisp.logging

import mu.KLogger
import mu.KotlinLogging
import org.slf4j.MDC
import org.slf4j.event.Level

typealias Tag = Pair<String, Any?>

inline fun <reified T> getLogger(): KLogger {
    return KotlinLogging.logger(T::class.qualifiedName!!)
}

fun KLogger.info(vararg tags: Tag, message: () -> Any?) =
    log(Level.INFO, message = message, tags = tags)

fun KLogger.warn(vararg tags: Tag, message: () -> Any?) =
    log(Level.WARN, message = message, tags = tags)

fun KLogger.error(vararg tags: Tag, message: () -> Any?) =
    log(Level.ERROR, message = message, tags = tags)

fun KLogger.debug(vararg tags: Tag, message: () -> Any?) =
    log(Level.DEBUG, message = message, tags = tags)

fun KLogger.info(th: Throwable, vararg tags: Tag, message: () -> Any?) =
    log(Level.INFO, th, message = message, tags = tags)

fun KLogger.warn(th: Throwable, vararg tags: Tag, message: () -> Any?) =
    log(Level.WARN, th, message = message, tags = tags)

fun KLogger.error(th: Throwable, vararg tags: Tag, message: () -> Any?) =
    log(Level.ERROR, th, message = message, tags = tags)

fun KLogger.debug(th: Throwable, vararg tags: Tag, message: () -> Any?) =
    log(Level.DEBUG, th, message = message, tags = tags)

fun KLogger.log(level: Level, vararg tags: Tag, message: () -> Any?) {
    withTags(*tags) {
        when (level) {
            Level.ERROR -> error(message)
            Level.WARN -> warn(message)
            Level.INFO -> info(message)
            Level.DEBUG -> debug(message)
            Level.TRACE -> trace(message)
        }
    }
}

fun KLogger.log(level: Level, th: Throwable, vararg tags: Tag, message: () -> Any?) {
    withTags(*tags) {
        when (level) {
            Level.ERROR -> error(th, message)
            Level.INFO -> info(th, message)
            Level.WARN -> warn(th, message)
            Level.DEBUG -> debug(th, message)
            Level.TRACE -> trace(th, message)
        }
    }
}

private fun withTags(vararg tags: Tag, f: () -> Unit) {
    // Establish MDC, saving prior MDC
    val priorMDC = tags.map { (k, v) ->
        val priorValue = MDC.get(k)
        MDC.put(k, v.toString())
        k to priorValue
    }

    try {
        f()
    } finally {
        // Restore or clear prior MDC
        priorMDC.forEach { (k, v) -> if (v == null) MDC.remove(k) else MDC.put(k, v) }
    }
}

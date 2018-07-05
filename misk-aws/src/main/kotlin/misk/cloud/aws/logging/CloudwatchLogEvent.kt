package misk.cloud.aws.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy

// TODO(mmihic): Span id, other HTTP data from action
internal data class CloudwatchLogEvent(
  val level: String,
  val logger: String,
  val message: String,
  val timestamp: Long,
  val thread: String? = null,
  val context: Map<String, String> = mapOf(),
  val throwable_proxy: ThrowableProxy? = null
) {
  constructor(event: ILoggingEvent) : this(
      level = event.level.levelStr,
      message = event.formattedMessage,
      thread = event.threadName ?: Thread.currentThread().name,
      timestamp = event.timeStamp,
      logger = event.loggerName,
      throwable_proxy = event.throwableProxy?.let { ThrowableProxy(it) },
      context = event.mdcPropertyMap.toMap()
  )
}

internal data class ThrowableProxy(
  val message: String,
  val class_name: String,
  val stack_trace_elements: List<String>,
  val cause: ThrowableProxy? = null
) {
  constructor(proxy: IThrowableProxy) : this(
      message = proxy.message,
      class_name = proxy.className,
      stack_trace_elements = proxy.stackTraceElementProxyArray.map {
        it.steAsString
      }.toList(),
      cause = proxy.cause?.let { ThrowableProxy(it) }
  )
}
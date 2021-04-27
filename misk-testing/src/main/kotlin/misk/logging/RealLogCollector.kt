package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.google.common.util.concurrent.AbstractIdleService
import wisp.logging.WispQueuedLogCollector
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
internal class RealLogCollector @Inject constructor() :
  AbstractIdleService(),
  LogCollector,
  wisp.logging.LogCollector,
  LogCollectorService {

  private val wispQueuedLogCollector = WispQueuedLogCollector()

  override fun takeMessages(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): List<String> = takeEvents(loggerClass, minLevel, pattern).map { it.message }

  override fun takeMessage(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): String = takeEvent(loggerClass, minLevel, pattern).message

  override fun takeEvents(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): List<ILoggingEvent> {
    return wispQueuedLogCollector.takeEvents(loggerClass, minLevel, pattern)
  }

  override fun takeEvent(loggerClass: KClass<*>?, minLevel: Level, pattern: Regex?): ILoggingEvent {
    return wispQueuedLogCollector.takeEvent(loggerClass, minLevel, pattern)
  }

  override fun startUp() {
    wispQueuedLogCollector.startUp()
  }

  override fun shutDown() {
    wispQueuedLogCollector.shutDown()
  }
}

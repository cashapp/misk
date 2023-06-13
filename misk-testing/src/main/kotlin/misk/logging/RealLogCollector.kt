package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.google.common.util.concurrent.AbstractIdleService
import wisp.logging.LogCollector
import wisp.logging.WispQueuedLogCollector
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
internal class RealLogCollector @Inject constructor(
  private val wispQueuedLogCollector: WispQueuedLogCollector
) : AbstractIdleService(),
  LogCollector by wispQueuedLogCollector,
  LogCollectorService {

  override fun startUp() {
    wispQueuedLogCollector.startUp()
  }

  override fun shutDown() {
    wispQueuedLogCollector.shutDown()
  }
}

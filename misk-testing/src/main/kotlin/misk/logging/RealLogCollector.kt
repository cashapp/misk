package misk.logging

import com.google.common.util.concurrent.AbstractIdleService
import wisp.logging.LogCollector
import wisp.logging.WispQueuedLogCollector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RealLogCollector @Inject constructor(
  private val wispQueuedLogCollector: WispQueuedLogCollector
) : AbstractIdleService(), LogCollector by wispQueuedLogCollector, LogCollectorService {

  override fun startUp() {
    wispQueuedLogCollector.startUp()
  }

  override fun shutDown() {
    wispQueuedLogCollector.shutDown()
  }
}

package misk.time

import com.google.common.base.Ticker
import misk.inject.KAbstractModule

internal class TickerModule : KAbstractModule() {
  override fun configure() {
    bind<Ticker>().toInstance(Ticker.systemTicker())
  }
}

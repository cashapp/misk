package misk.time

import com.google.common.base.Ticker
import com.google.common.testing.FakeTicker
import misk.inject.KAbstractModule

class FakeTickerModule : KAbstractModule() {
  override fun configure() {
    bind<Ticker>().to<FakeTicker>()
    bind<FakeTicker>().toInstance(FakeTicker())
  }
}

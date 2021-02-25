package misk.time

import misk.inject.KAbstractModule
import java.time.Clock

class FakeClockModule : KAbstractModule() {
  override fun configure() {
    bind<Clock>().to<FakeClock>()
    bind<FakeClock>().toInstance(fakeClock)
  }

  companion object {
    val fakeClock by lazy { FakeClock() }
  }
}

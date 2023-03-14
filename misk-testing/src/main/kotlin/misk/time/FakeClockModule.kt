package misk.time

import misk.inject.KInstallOnceModule
import java.time.Clock
import wisp.time.FakeClock as WispFakeClock

class FakeClockModule : KInstallOnceModule() {
  override fun configure() {
    val fakeClock = FakeClock()
    bind<Clock>().toInstance(fakeClock)
    bind<FakeClock>().toInstance(fakeClock)
    bind<WispFakeClock>().toInstance(fakeClock)
  }
}

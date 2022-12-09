package misk.time

import misk.inject.KInstallOnceModule
import java.time.Clock
import wisp.time.FakeClock as WispFakeClock

class FakeClockModule : KInstallOnceModule() {
  override fun configure() {
    bind<Clock>().to<WispFakeClock>()
    val fakeClock = FakeClock()
    bind<FakeClock>().toInstance(fakeClock)
    bind<WispFakeClock>().toInstance(fakeClock.wispFakeClock)
  }
}

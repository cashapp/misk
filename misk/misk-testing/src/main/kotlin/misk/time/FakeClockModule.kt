package misk.time

import misk.inject.KInstallOnceModule
import java.time.Clock
import wisp.time.FakeClock as WispFakeClock

class FakeClockModule : KInstallOnceModule() {
  override fun configure() {
    bind<Clock>().to<WispFakeClock>()
    val wispFakeClock = WispFakeClock()
    bind<WispFakeClock>().toInstance(wispFakeClock)
    bind<FakeClock>().toInstance(FakeClock(wispFakeClock = wispFakeClock))
  }
}

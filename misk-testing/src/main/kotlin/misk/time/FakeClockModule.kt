package misk.time

import misk.inject.KInstallOnceModule
import java.time.Clock
import wisp.time.FakeClock as WispFakeClock

class FakeClockModule : KInstallOnceModule() {
  override fun configure() {
    bind<Clock>().to<FakeClock>()
    bind<WispFakeClock>().to<FakeClock>()
  }
}

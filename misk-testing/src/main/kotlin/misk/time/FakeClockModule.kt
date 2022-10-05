package misk.time

import misk.inject.KInstallOnceModule
import java.time.Clock

class FakeClockModule : KInstallOnceModule() {
  override fun configure() {
    bind<Clock>().to<FakeClock>()
    bind<FakeClock>().toInstance(FakeClock())
  }
}

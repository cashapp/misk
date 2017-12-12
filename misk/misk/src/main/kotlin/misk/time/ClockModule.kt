package misk.time

import misk.inject.KAbstractModule
import java.time.Clock

class ClockModule : KAbstractModule() {
  override fun configure() {
    bind<Clock>().toInstance(Clock.systemUTC())
  }
}

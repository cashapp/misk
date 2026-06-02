package misk.time

import java.time.Clock
import misk.ServiceModule
import misk.inject.KAbstractModule

internal class ClockModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<ForceUtcTimeZoneService>())
    bind<Clock>().toInstance(Clock.systemUTC())
  }
}

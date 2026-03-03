package misk.time

import misk.ServiceModule
import misk.inject.KAbstractModule
import java.time.Clock

internal class ClockModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<ForceUtcTimeZoneService>())
    bind<Clock>().toInstance(Clock.systemUTC())
  }
}

package misk.time

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import java.time.Clock

internal class ClockModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<ForceUtcTimeZoneService>()

    bind<Clock>().toInstance(Clock.systemUTC())
  }
}

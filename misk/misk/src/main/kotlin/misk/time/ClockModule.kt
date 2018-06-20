package misk.time

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to
import java.time.Clock

class ClockModule : KAbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<Service>().to<ForceUtcTimeZoneService>()

    bind<Clock>().toInstance(Clock.systemUTC())
  }
}

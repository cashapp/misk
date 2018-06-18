package misk.time

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import java.time.Clock

class ClockModule : KAbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<Service>().to(ForceUtcTimeZoneService::class.java)

    bind<Clock>().toInstance(Clock.systemUTC())
  }
}

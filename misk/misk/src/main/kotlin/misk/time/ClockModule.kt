package misk.time

import misk.inject.KAbstractModule
import java.time.Clock
import java.time.ZoneOffset
import java.util.TimeZone

class ClockModule : KAbstractModule() {
  override fun configure() {
    bind<Clock>().toInstance(Clock.systemUTC())
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC))
  }
}

package misk.time

import com.google.inject.AbstractModule
import java.time.Clock

class ClockModule : AbstractModule() {
  override fun configure() {
    bind(Clock::class.java).toInstance(Clock.systemUTC())
  }
}

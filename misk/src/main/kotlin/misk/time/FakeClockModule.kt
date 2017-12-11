package misk.time

import com.google.inject.AbstractModule
import java.time.Clock

class FakeClockModule : AbstractModule() {
    override fun configure() {
        bind(Clock::class.java).to(FakeClock::class.java)
        bind(FakeClock::class.java).asEagerSingleton()
    }
}
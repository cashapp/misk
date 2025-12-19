package misk.time

import com.google.inject.Provides
import com.google.inject.multibindings.ProvidesIntoSet
import jakarta.inject.Singleton
import java.time.Clock
import misk.inject.KInstallOnceModule
import misk.testing.TestFixture

class FakeClockModule : KInstallOnceModule() {
  @Provides @Singleton fun provideFakeClock(): FakeClock = FakeClock()

  @Provides fun provideClock(fakeClock: FakeClock): Clock = fakeClock

  @ProvidesIntoSet fun provideTestFixture(fakeClock: FakeClock): TestFixture = fakeClock
}

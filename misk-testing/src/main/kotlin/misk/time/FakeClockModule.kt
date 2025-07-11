package misk.time

import com.google.inject.Provides
import com.google.inject.multibindings.ProvidesIntoSet
import jakarta.inject.Singleton
import misk.inject.KInstallOnceModule
import misk.testing.TestFixture
import java.time.Clock

class FakeClockModule : KInstallOnceModule() {
  @Provides @Singleton
  fun provideFakeClock(): FakeClock = FakeClock()
  
  @Provides 
  fun provideClock(fakeClock: FakeClock): Clock = fakeClock
  
  @ProvidesIntoSet
  fun provideTestFixture(fakeClock: FakeClock): TestFixture = fakeClock
}

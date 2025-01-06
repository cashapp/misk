package misk.concurrent

import misk.inject.KAbstractModule
import misk.testing.TestFixture

class FakeSleeperModule : KAbstractModule() {
  override fun configure() {
    bind<Sleeper>().to<FakeSleeper>()
    multibind<TestFixture>().to<FakeSleeper>()
  }
}

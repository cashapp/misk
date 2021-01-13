package misk.concurrent

import misk.inject.KAbstractModule

class FakeSleeperModule : KAbstractModule() {
  override fun configure() {
    bind<Sleeper>().to<FakeSleeper>()
  }
}

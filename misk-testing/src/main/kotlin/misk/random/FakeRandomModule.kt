package misk.random

import misk.inject.KAbstractModule

class FakeRandomModule : KAbstractModule() {
  override fun configure() {
    bind<Random>().to<FakeRandom>()
    bind<ThreadLocalRandom>().to<FakeThreadLocalRandom>()
  }
}

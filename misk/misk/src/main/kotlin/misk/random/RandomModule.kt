package misk.random

import misk.inject.KAbstractModule


internal class RandomModule : KAbstractModule() {
  override fun configure() {
    bind<Random>()
    bind<ThreadLocalRandom>()
  }
}

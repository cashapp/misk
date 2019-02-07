package misk.concurrent

import misk.inject.KAbstractModule

internal class SleeperModule : KAbstractModule() {
  override fun configure() {
    bind<Sleeper>().toInstance(Sleeper.DEFAULT)
  }
}

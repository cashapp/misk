package misk.flags.memory

import misk.flags.FlagStore
import misk.inject.KAbstractModule
import misk.inject.asSingleton

class InMemoryFlagStoreModule : KAbstractModule() {
  override fun configure() {
    bind<FlagStore>().to<InMemoryFlagStore>().asSingleton()
  }
}

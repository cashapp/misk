package misk.flags.memory

import misk.flags.FlagStore
import misk.inject.KAbstractModule

class InMemoryFlagStoreModule : KAbstractModule() {
  override fun configure() {
    bind<FlagStore>().to<InMemoryFlagStore>()
  }
}

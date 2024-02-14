package misk.logging

import misk.inject.KAbstractModule
import misk.inject.asSingleton

internal class MdcModule : KAbstractModule() {

  override fun configure() {
    bind<Mdc>().to<MiskMdc>().asSingleton()
  }
}

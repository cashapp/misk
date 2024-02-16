package misk.logging

import misk.inject.KAbstractModule

internal class MdcModule : KAbstractModule() {

  override fun configure() {
    bind<Mdc>().toInstance(MiskMdc)
  }
}

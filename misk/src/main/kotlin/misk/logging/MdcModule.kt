package misk.logging

import misk.inject.KInstallOnceModule

class MdcModule : KInstallOnceModule() {

  override fun configure() {
    bind<Mdc>().toInstance(MiskMdc)
  }
}

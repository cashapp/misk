package misk.hibernate.testing

import misk.hibernate.PrimitivesDb
import misk.hibernate.PrimitivesDbTestModule
import misk.inject.KAbstractModule

class TransacterFaultInjectorTestModule : KAbstractModule() {
  override fun configure() {
    install(PrimitivesDbTestModule())
    install(TransacterFaultInjectorModule(qualifier = PrimitivesDb::class))
  }
}

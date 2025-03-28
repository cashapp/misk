package misk.audit

import misk.inject.KAbstractModule
import misk.inject.asSingleton

class FakeAuditClientModule: KAbstractModule() {
  override fun configure() {
    bind<AuditClient>().to<FakeAuditClient>().asSingleton()
  }
}

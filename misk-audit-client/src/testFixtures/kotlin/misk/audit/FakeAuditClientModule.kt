package misk.audit

import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.testing.TestFixture
import misk.web.interceptors.hooks.AuditClientHook
import misk.web.interceptors.hooks.RequestResponseHook

class FakeAuditClientModule : KAbstractModule() {
  override fun configure() {
    bind<AuditClient>().to<FakeAuditClient>().asSingleton()
    multibind<TestFixture>().to<FakeAuditClient>()
    multibind<RequestResponseHook.Factory>().to<AuditClientHook.Factory>()
  }
}

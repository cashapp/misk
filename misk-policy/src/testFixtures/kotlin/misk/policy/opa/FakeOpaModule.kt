package misk.policy.opa

import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.testing.TestFixture

class FakeOpaModule @Inject constructor() : KAbstractModule() {
  override fun configure() {
    bind<OpaPolicyEngine>().to<FakeOpaPolicyEngine>()
    multibind<TestFixture>().to<FakeOpaPolicyEngine>()
  }
}

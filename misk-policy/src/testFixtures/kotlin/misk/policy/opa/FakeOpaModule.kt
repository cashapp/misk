package misk.policy.opa

import misk.inject.KAbstractModule
import jakarta.inject.Inject
import misk.testing.TestFixture

class FakeOpaModule @Inject constructor(): KAbstractModule()  {
  override fun configure() {
    bind<OpaPolicyEngine>().to<FakeOpaPolicyEngine>()
    multibind<TestFixture>().to<FakeOpaPolicyEngine>()
  }
}

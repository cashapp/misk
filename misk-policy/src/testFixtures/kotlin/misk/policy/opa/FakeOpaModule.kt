package misk.policy.opa

import misk.inject.KAbstractModule
import jakarta.inject.Inject

class FakeOpaModule @Inject constructor(): KAbstractModule()  {
  override fun configure() {
    bind<OpaPolicyEngine>().to<FakeOpaPolicyEngine>()
  }
}

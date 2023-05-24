package misk.policy.opa

import misk.inject.KAbstractModule
import javax.inject.Inject

class FakeOpaModule @Inject constructor(): KAbstractModule()  {
  override fun configure() {
    bind<OpaPolicyEngine>().to<FakeOpaPolicyEngine>()
  }
}

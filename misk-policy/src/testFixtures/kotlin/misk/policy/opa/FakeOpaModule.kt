package misk.policy.opa

import misk.inject.KAbstractModule
import com.google.inject.Inject

class FakeOpaModule @Inject constructor(): KAbstractModule()  {
  override fun configure() {
    bind<OpaPolicyEngine>().to<FakeOpaPolicyEngine>()
  }
}

package misk.policy.opa

import misk.inject.KAbstractModule
import com.google.inject.Inject

@Deprecated("Replace the dependency on misk-policy-testing with testFixtures(misk-policy)")
class FakeOpaModule @Inject constructor(): KAbstractModule()  {
  override fun configure() {
    bind<OpaPolicyEngine>().to<FakeOpaPolicyEngine>()
  }
}

package misk.policy.opa

import misk.inject.KAbstractModule
import java.nio.file.Paths
import javax.inject.Inject

class OpaTestingModule @Inject constructor(
  private val config: OpaTestConfig
) : KAbstractModule() {

  override fun configure() {
    bind<OpaTestConfig>().toInstance(config)
  }
}

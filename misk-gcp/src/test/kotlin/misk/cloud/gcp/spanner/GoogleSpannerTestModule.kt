package misk.cloud.gcp.spanner

import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.ReusableTestModule
import wisp.deployment.TESTING

class GoogleSpannerTestModule(val config: SpannerConfig) : ReusableTestModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DeploymentModule(TESTING))
    install(GoogleSpannerEmulatorModule(config))
    install(GoogleSpannerModule(config))
  }
}

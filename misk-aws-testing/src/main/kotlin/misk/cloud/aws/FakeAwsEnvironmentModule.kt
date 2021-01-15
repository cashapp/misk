package misk.cloud.aws

import misk.environment.ForEnvVars
import misk.inject.KAbstractModule

/**
 * [FakeAwsEnvironmentModule] pulls region and account information from an in memory map.
 */
class FakeAwsEnvironmentModule : KAbstractModule() {
  override fun configure() {
    newMapBinder<String, String>(ForEnvVars::class)
        .addBinding("REGION").toInstance("us-east-1")
    newMapBinder<String, String>(ForEnvVars::class)
        .addBinding("ACCOUNT_ID").toInstance("8675309")
  }
}
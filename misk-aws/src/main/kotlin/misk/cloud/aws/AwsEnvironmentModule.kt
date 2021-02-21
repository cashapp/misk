package misk.cloud.aws

import com.google.inject.Provides
import misk.environment.EnvVarLoader
import misk.environment.ForEnvVars
import misk.inject.KAbstractModule

/** [AwsEnvironmentModule] pulls region and account information from installed env vars */
class AwsEnvironmentModule : KAbstractModule() {

  @Provides fun awsRegion(envVarLoader: EnvVarLoader): AwsRegion {
    return AwsRegion(envVarLoader.getEnvVar("REGION"))
  }

  @Provides fun awsAccountId(envVarLoader: EnvVarLoader): AwsAccountId {
    return AwsAccountId(envVarLoader.getEnvVar("ACCOUNT_ID"))
  }
}

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

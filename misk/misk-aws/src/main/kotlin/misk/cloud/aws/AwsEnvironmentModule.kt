package misk.cloud.aws

import com.google.inject.Provides
import misk.environment.EnvVarLoader
import misk.environment.ForEnvVars
import misk.inject.KAbstractModule
import wisp.aws.environment.AwsEnvironment

/** [AwsEnvironmentModule] pulls region and account information from installed env vars */
class AwsEnvironmentModule : KAbstractModule() {

  @Provides fun awsRegion(envVarLoader: EnvVarLoader): AwsRegion {
    return AwsEnvironment.awsRegion(envVarLoader).toMiskAwsRegion()
  }

  @Provides fun awsAccountId(envVarLoader: EnvVarLoader): AwsAccountId {
    return AwsEnvironment.awsAccountId(envVarLoader).toMiskAwsAccountId()
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

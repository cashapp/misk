package misk.cloud.aws

import com.google.inject.Provides
import misk.environment.EnvVarLoader
import misk.environment.ForEnvVars
import misk.inject.KAbstractModule
import wisp.aws.environment.AwsEnvironment

/** [AwsEnvironmentModule] pulls region and account information from installed env vars */
class AwsEnvironmentModule : KAbstractModule() {

  private val delegate = AwsEnvironment()

  @Provides fun awsRegion(envVarLoader: EnvVarLoader): AwsRegion {
    val awsRegion = delegate.awsRegion(envVarLoader)
    return awsRegion.toMiskAwsRegion()
  }

  @Provides fun awsAccountId(envVarLoader: EnvVarLoader): AwsAccountId {
    val awsAccountId = delegate.awsAccountId(envVarLoader)
    return awsAccountId.toMiskAwsAccountId()
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

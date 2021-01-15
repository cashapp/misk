package misk.cloud.aws

import com.google.inject.Provides
import misk.environment.EnvVarLoader
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
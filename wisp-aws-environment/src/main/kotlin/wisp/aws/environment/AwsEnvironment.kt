package wisp.aws.environment

import wisp.deployment.EnvironmentVariableLoader

/** [AwsEnvironment] pulls region and account information from installed
 * environment variables
 */
class AwsEnvironment {

  fun awsRegion(environmentVariableLoader: EnvironmentVariableLoader): AwsRegion {
    return AwsRegion(environmentVariableLoader.getEnvironmentVariable("REGION"))
  }

  fun awsAccountId(environmentVariableLoader: EnvironmentVariableLoader): AwsAccountId {
    return AwsAccountId(environmentVariableLoader.getEnvironmentVariable("ACCOUNT_ID"))
  }
}

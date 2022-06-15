package wisp.aws.environment

import wisp.deployment.EnvironmentVariableLoader

/** [AwsEnvironment] pulls region and account information from installed
 * environment variables
 *
 * Note: Because of different package names for AWS1 vs AWS2 sdks, we can't use AWS's Region class.
 */
object AwsEnvironment {

    fun awsRegion(
        environmentVariableLoader: EnvironmentVariableLoader = EnvironmentVariableLoader.real,
        environmentVariables: List<String> = listOf("REGION", "AWS_REGION"),
        defaultAwsRegion: String = "us-west-2"
    ): AwsRegion {
        for (environmentVariable in environmentVariables) {
            val region =
                environmentVariableLoader.getEnvironmentVariableOrDefault(environmentVariable, "")
            if (region.isNotEmpty()) {
                return AwsRegion(region)
            }
        }
        return AwsRegion(defaultAwsRegion)
    }

    fun awsAccountId(
        environmentVariableLoader: EnvironmentVariableLoader = EnvironmentVariableLoader.real,
        environmentVariable: String = "ACCOUNT_ID",
    ): AwsAccountId =
        AwsAccountId(environmentVariableLoader.getEnvironmentVariable(environmentVariable))
}

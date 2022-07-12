# wisp-aws-environment

For AWS information from the environment, with easy means for overrides for testing.  Currently
supports AWS Region and Account ID information.

Also see [wisp-deployment-testing](https://github.com/cashapp/wisp/tree/master/wisp-deployment-testing)
for the [FakeEnvironmentVariableLoader](https://github.com/cashapp/wisp/blob/master/wisp-deployment-testing/src/main/kotlin/wisp/deployment/FakeEnvironmentVariableLoader.kt)
to use in tests to set Fake environment variables.

## Usage

By default, the AWS Region will be read from either "REGION" or "AWS_REGION" environment variables.

```kotlin
val awsRegion: AwsRegion = AwsEnvironment.awsRegion() 
```

By default, the AWS Account ID will be read from the "ACCOUNT_ID" environment variable.

```kotlin
val awsAccountId: AccountId = AwsEnvironment.awsAccountId()
```

Using your own custom environment variables

```kotlin
val awsRegion: AwsRegion = AwsEnvironment.awsRegion(environmentVariables = listOf("MY_REGION_ENV_VAR"))
val awsAccountId: AccountId = AwsEnvironment.awsAccountId(environmentVariable = "MY_ACCOUNT_ID_ENV_VAR")
```

Default fallback for the AWS Region if the environment variable(s) are not set

```kotlin
val awsRegion: AwsRegion = AwsEnvironment.awsRegion(defaultAwsRegion = "us-west-2") 
```

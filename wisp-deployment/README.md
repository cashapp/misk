# wisp-deployment

This module provides information about the applications deployment and environment.

[Deployment](https://github.com/cashapp/misk/blob/master/wisp-deployment/src/main/kotlin/wisp/deployment/Deployment.kt)
contains information on where the application is deployed, that is, in Production, Staging, Testing or
Development environments.  

Deployments can be created manually, or by examining an environment variable (default environment
variable is ENVIRONMENT).

Also see [wisp-deployment-testing](https://github.com/cashapp/misk/tree/master/wisp-deployment-testing)
for the [FakeEnvironmentVariableLoader](https://github.com/cashapp/misk/blob/master/wisp-deployment-testing/src/main/kotlin/wisp/deployment/FakeEnvironmentVariableLoader.kt)
to use in tests to set Fake environment variables.

## Usage

The following manually creates a production Deployment   

```kotlin
val deployment: Deployment = Deployment(
  "My Deployment Name",
  isProduction = true,
  isStaging = false,
  isTest = false,
  isLocalDevelopment = false
)
```

There are 4 preset deployments: PRODUCTION, STAGING, TESTING and DEVELOPMENT.  One of these will be
returned if creating a deployment from the environment variable, with a default of DEVELOPMENT if
the environment variable is not set.

```kotlin
val deployment: Deployment = Deployment.getDeploymentFromEnvironmentVariable()

if (deployment.isProduction) {
  // this path will be executed
}

if (deployment.isDevelopment) {
  // this path will not be executed
}
```

For testing, to set a specific Deployment, you can either create it manually, or override the 
environment variable using a FakeEnvironmentVariableLoader.


```kotlin
val environmentVariableLoader: FakeEnvironmentVariableLoader = 
  FakeEnvironmentVariableLoader(mutableMapOf("ENVIRONMENT" to "Staging"))

val deployment = getDeploymentFromEnvironmentVariable(
  environmentVariableLoader = environmentVariableLoader
)

if (deployment.isStaging) {
  // this path will be executed
}
```
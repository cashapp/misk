# wisp-deployment-testing

This module provides
a [FakeEnvironmentVariableLoader](https://github.com/cashapp/wisp/blob/main/wisp-deployment-testing/src/main/kotlin/wisp/deployment/FakeEnvironmentVariableLoader.kt)
as an implementation of
the [EnvironmentVariableLoader](https://github.com/cashapp/wisp/blob/main/wisp-deployment/src/main/kotlin/wisp/deployment/EnvironmentVariableLoader.kt)
to use in tests to set Fake environment variables. Ideally, you should not use this package in any production code, but
for test purposes only.

Also see [wisp-deployment](https://github.com/cashapp/wisp/tree/main/wisp-deployment).

## Usage

```kotlin
val environmentVariableLoader: FakeEnvironmentVariableLoader = 
  FakeEnvironmentVariableLoader(
    mutableMapOf(
      "ENVIRONMENT" to "Staging",
      "FOO" to "Bar"
    )
  )

val deployment = getDeploymentFromEnvironmentVariable(
  environmentVariableLoader = environmentVariableLoader
)

if (deployment.isStaging) {
  // this path will be executed
}

// foo will be "Bar"
val foo =   environmentVariableLoader.getEnvironmentVariable("FOO")

// Unknown env var will throw IllegalStateException
val unknown = environmentVariableLoader.getEnvironmentVariable("Unknown")

// Unknown env var with a fallback value will return fallback instead of throwing IllegalStateException
val unknownWithDefaultFallback = environmentVariableLoader.getEnvironmentVariable("UNKNOWN", "FALLBACK") // "FALLBACK"
```

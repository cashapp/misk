# wisp-launchdarkly

FeatureFlags implementation using [LaunchDarkly](https://launchdarkly.com/).

See [wisp-feature](https://github.com/cashapp/wisp/tree/main/wisp-feature) for more details on feature flags.

See [wisp-feature-testing](https://github.com/cashapp/wisp/tree/main/wisp-feature-testing)
for details on a Fake implementation for use in tests.

## Usage

```kotlin
// create your LaunchDarkly config (or load it using wisp-config)
val config = LaunchDarklyConfig(sdk_key = "...", base_uri = "...")

val sslLoader = SslLoader(ResourceLoader.SYSTEM)
val sslContextFactory = SslContextFactory(sslLoader)

// create the LaunchDarkly client
val ldClient = LaunchDarklyClient.createLaunchDarklyClient(
  config = config,
  sslLoader = sslLoader,
  sslContextFactory = sslContextFactory,
  resourceLoader = ResourceLoader.SYSTEM
)

val moshi = DEFAULT_KOTLIN_MOSHI

val ldFeatureFlags = LaunchDarklyFeatureFlags(
  ldClient = ldClient,
  moshi = moshi
)

// TODO - usages...
```

# wisp-feature

FeatureFlags ....

See [wisp-launchdarkly](https://github.com/cashapp/misk/tree/master/wisp-feature) for more an 
implementation of FeatureFlags using [LaunchDarkly](https://launchdarkly.com/).

See [wisp-feature-testing](https://github.com/cashapp/misk/tree/master/wisp-feature-testing) 
for details on a Fake implementation for use in tests.

## Usage - Strongly Typed

### Basic Usage

Step 1: Define your feature flag in Launch Darkly

Step 2: Define the flag in your code:

```kotlin
data class MissleButtonShouldBeEnabled(
  // Put the launch darkly key and attributes here, use real types!
  val customerId: String,
  val region: Region,
  val cardBin: CardBIN,
) : BooleanFeatureFlag { // Also available: String, Double, Int, Enum, Json
  // `feature` needs to match the name in LaunchDarkly
  override val feature = Feature("missle-button-should-be-enabled")
  
  // `key` should be the field you want to use as the LaunchDarkly key
  override val key = customerId
  
  // `attributes` should contain all the other fields, except for `key`
  override val attributes = Attributes()
    .with("cardBin", cardBin.toString()) // `with` supports `String` and `Number`
    .with("region", region.toString())
}
```

Step 3: Use your flag (`MissleButtonShouldBeEnabled`) with a `wisp-feature` client (i.e. `wisp-launchdarkly` 
or `wisp-feature-testing`):

```
val client: FeatureFlags = ... // see `wisp-launchdarkly` or `wisp-feature-testing` for how to get a client

// `get`s return type depends on the flag type, in this case it's `Boolean`
val enableMissleButton = client.get(
  MissleButtonShouldBeEnabled(
    customerId = "customer-1234",
    region = Region.Australia,
    cardBin = CardBIN("451213")
  )
)

// ... do things with `enableMissleButton`
```

## Usage - Legacy

```kotlin
// TODO - usages...
```
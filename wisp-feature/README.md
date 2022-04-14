# wisp-feature

FeatureFlags ....

See [wisp-launchdarkly](https://github.com/cashapp/misk/tree/master/wisp-launchdarkly) for more an 
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
  // `feature` needs to match the feature name in LaunchDarkly
  override val feature = Feature("missle-button-should-be-enabled")
  
  // `key` should be the field you want to use as the LaunchDarkly key
  override val key = customerId
  
  // `attributes` should contain all the other fields, except for `key`. 
  //
  // If using LaunchDarkly, these will be sent as custom attributes.
  override val attributes = Attributes()
    .with("cardBin", cardBin.toString()) // `with` supports `String` and `Number`
    .with("region", region.toString())
}
```

Step 3: Use your flag (`MissleButtonShouldBeEnabled`) with a `wisp-feature` client (i.e. `wisp-launchdarkly` 
or `wisp-feature-testing`):

```kotlin
val featureFlags: FeatureFlags = ??? // see `wisp-launchdarkly` or `wisp-feature-testing` for how to get `featureFlags`

// `get`s return type depends on the flag type, in this case it's `Boolean`
val enableMissleButton = featureFlags.get(
  MissileButtonShouldBeEnabled(
    customerId = "customer-1234",
    region = Region.Australia,
    cardBin = CardBIN("451213")
  )
)

// ... do things with `enableMissleButton`
```

### Strongly Typed JSON Flags

```kotlin
data class DomainObject(val name: String, val age: Int)

data class FeatureFlagThatReturnsJson(
  // Put launch darkly key and attributes here, same as usual.  
  val customerId: String,
  val region: Region
) : JsonFeatureFlag<DomainObject> {
  // `feature` needs to match the feature name in LaunchDarkly
  override val feature = Feature("missle-button-should-be-enabled")

  // `key` should be the field you want to use as the LaunchDarkly key
  override val key = customerId

  // `attributes` should contain all the other fields, except for `key`. 
  //
  // If using LaunchDarkly, these will be sent as custom attributes.
  override val attributes = Attributes()
    .with("region", region.toString())
    
  // `returnType` need to match the generic type provided to `JsonFeatureFlag  
  override val returnType = DomainObject::class.java  
}
```

Calling `get(FeatureFlagThatReturnsJson(...))` will return a `DomainObject`

### Migrating from Legacy flags

Want to get on the strongly-typed hype train? Fantastic! Here's what you need to do:

1. Introduce a strongly typed flag that matches your current use of `getString`/`getBoolean`/`getX`.
1. Replace all usages of `getString`/`getBoolean`/`getX` with a call to `get` using the flag
1. Replace all test usages of `override(String)`/`override(Boolean)`/`override(X)` with `override<MyStrongFlag>`

For example, consider this existing legacy implementation:

```kotlin
// FILE: ApplicationFeatureFlags.kt
val MY_FEATURE_FLAG = Feature("my-feature-flag")

// FILE: MyService.kt
class MyService(private val featureFlags: FeatureFlags) {
  // Business logic function that uses feature flags
  fun getInstructions(customerId: String): String {
    val myFeatureFlagEnabled = featureFlags.getBoolean(
      feature = MY_FEATURE_FLAG,
      key = customerId,
      attributes = Attributes(
        mapOf(
          "region" to Region.Australia.toString()
        )
      )
    )
    
    return if (myFeatureFlagEnabled) {
      "my feature is enabled"
    } else {
      "my feature is disabled"
    }
  }
}

// FILE: MyServiceTest.kt
class MyServiceTest() {
  @Test fun `my service should say the feature is enabled for mary, but disabled otherwise`() {
    val featureFlags = FakeFeatureFlags()
      .override(MY_FEATURE_FLAG, false)
      .override(MY_FEATURE_FLAG, "mary", true)
      
    val service = MyService(featureFlags)
    service.getInstruction("bob").shouldBe(false)
    service.getInstruction("mary").shouldBe(true)
  }
}
```

Looking at `MyService` we can see that this is a `Boolean` flag (since we are using `getBoolean`), the key is 
`customerId` and we have one attribute called `region`. Now we can migrate:

```kotlin
// FILE: ApplicationFeatureFlags.kt
data class MyFeatureFlag(
  // We include the key and attributes we identified before
  customerId: String,
  region: Region
): BooleanFeatureFlag { 
  // `feature` should be whatever `MY_FEATURE_FLAG` was before
  override val feature = Feature("my-feature-flag")

  // `key` should be the field we identified as the key from before
  override val key = customerId

  // `attributes` should contain all the other fields we identified
  // before
  override val attributes = Attributes()
    .with("region", region.toString())
}

// FILE: MyService.kt
class MyService(private val featureFlags: FeatureFlags) {
  // Business logic function that uses feature flags
  fun getInstructions(customerId: String): String {
    val myFeatureFlagEnabled = featureFlags.get( // <-- `get` instead of `getBoolean`
      MyFeatureFlag(customerId, Region.Australia)
    )
    
    return if (myFeatureFlagEnabled) {
      "my feature is enabled"
    } else {
      "my feature is disabled"
    }
  }
}

// FILE: MyServiceTest.kt
class MyServiceTest() {
  @Test fun `my service should say the feature is enabled for mary, but disabled otherwise`() {
    val featureFlags = FakeFeatureFlags()
      .override<MyFeatureFlag>(false)
      .override<MyFeatureFlag>(true) { it.customerId == "mary" }
      
    val service = MyService(featureFlags)
    service.getInstruction("bob").shouldBe(false)
    service.getInstruction("mary").shouldBe(true)
  }
}
```

## Usage - Legacy

```kotlin
// TODO - usages...
```

# wisp-feature-testing

Fake implementation of FeatureFlags.

See [wisp-feature](https://github.com/cashapp/misk/tree/master/wisp-feature) for more details on
feature flags.

## Usage - Strongly Typed

#### Step 1: Define a feature flag as described in [wisp-feature](https://github.com/cashapp/misk/tree/master/wisp-feature)

#### Step 2: Use the feature flag in your code:

For example, you might have a service like this:

```kotlin
class ThingToTest(private val featureFlags: FeatureFlags) {
  fun getInstructions(customerId: String): String {
    val missileButtonEnabled = featureFlags.get(
      MissileButtonShouldBeEnabled(
        customerId = customerId,
        region = Region.Australia,
        cardBin = CardBIN("451213")  
      )  
    )  
      
    return if (missleButtonEnabled) {
      "Press the missile button"  
    } else {
      "Panic!"  
    }
  }  
}
```

#### Step 3: Use `FakeFeatureFlags` to test `ThingToTest`:

```kotlin
class ThingToTestTests {
  @Test  
  fun `it should tell me to press the button if the missile button is enabled`() {
    val featureFlags = FakeFeatureFlags()
      .override<MissileButtonShouldBeEnabled>(true)
      
    val thing = ThingToTest(featureFlags)  
      
    thing.getInstructions(customerId = "fred").shouldBe("Press the missile button")
  }
    
  @Test  
  fun `it should tell me to panic if the missile button is not enabled`() {
    val featureFlags = FakeFeatureFlags()
      .override<MissileButtonShouldBeEnabled>(false)

    val thing = ThingToTest(featureFlags)

    thing.getInstructions(customerId = "alice").shouldBe("Panic!")
  }
}
```

#### Bonus Step: Only match flags for specific fields

Sometimes you want to say "only enable the missile button for Mary". The `override` function has an optional `matcher`
value that must be true for the override to match.

```kotlin
class ThingToTestTests {
  @Test
  fun `it should tell mary to press the button`() {
    val featureFlags = FakeFeatureFlags()
      .override<MissileButtonShouldBeEnabled>(true) { it.customerId == "mary" }

    val thing = ThingToTest(featureFlags)

    thing.getInstructions(customerId = "mary").shouldBe("Press the missile button")
  }
}
```


## Usage - Legacy

```kotlin

// TODO - usages...
```

# wisp-time-testing

Provides
a [FakeClock](https://github.com/cashapp/wisp/blob/main/wisp-time-testing/src/main/kotlin/wisp/time/FakeClock.kt), an
implementation of `java.time.Clock` that can be manipulated for testing components with logic that relies on clocks.

## Usage

```kotlin
val clock = FakeClock()

val foo = ThingThatNeedsClock(clock)
foo.doSomething()
clock.add(2, TimeUnit.SECOND)
foo.doSomething()
```

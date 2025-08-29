package misk.testing

import kotlin.reflect.KProperty

/**
 * Interface for test fixtures that need to be reset between test runs, when the reuse injector feature is enabled.
 *
 * This needs to be implemented by:
 *   1. stateful fakes which hold `var`s or mutable collections. The `reset` implementation should set the state to
 *   the initial values. In such case, prefer extending the `FakeFixture` class instead.
 *   2. test dependencies interacting with external stores, such as databases or caches. The `reset` implementation
 *   needs to clear the store.
 *
 * TestFixtures must be multibound in a Guice module to ensure that the test infrastructure can reset them between test runs.
 * For example:
 *
 * ```kotlin
 *    bind<Clock>().to<FakeClock>()
 *  + multibind<TestFixture>().to<FakeClock>()
 * ```
 */
interface TestFixture {

  /**
   * Called before each test run to reset the state of the fixture.
   */
  fun reset()
}

class ResettablePropertyDelegate<T>(private val initializer: () -> T) {
  private var value: T = initializer()

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T = this.value

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
  }

  fun reset() {
    this.value = initializer()
  }
}

/**
 * Base class providing a mechanism to define properties in fakes that will automatically be reset between test runs.
 * The properties are defined using the `resettable` function, which creates a resettable property delegate.
 *
 * ```kotlin
 * class FakeJwtVerifier @Inject constructor() : JwtVerifier, FakeFixture() {
 *   - private var succeeds = true
 *   + private var succeeds by resettable { true }
 *
 *   // more methods
 * }
 * ```
 */
open class FakeFixture : TestFixture {
  private val delegates = mutableListOf<ResettablePropertyDelegate<*>>()

  override fun reset() {
    delegates.forEach { it.reset() }
  }

  fun <T> resettable(initializer: () -> T): ResettablePropertyDelegate<T> {
    val delegate = ResettablePropertyDelegate(initializer)
    delegates.add(delegate)
    return delegate
  }
}

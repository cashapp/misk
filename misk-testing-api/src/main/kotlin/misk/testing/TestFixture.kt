package misk.testing

/**
 * Interface for test fixtures that need to be reset between test runs, when the reuse injector feature is enabled.
 *
 * This needs to be implemented by:
 *   1. stateful fakes which hold `var`s or mutable collections. The `reset` implementation should set the state to
 *   the initial values.
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

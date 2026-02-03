package misk.inject

import misk.testing.TestFixture

/**
 * Module that binds [FakeSwitch] as both a [Switch] and [AsyncSwitch], and registers it as a [TestFixture] to ensure it
 * gets reset between test runs.
 *
 * Example usage:
 * ```kotlin
 * @MiskTest
 * class MyTest {
 *   @MiskTestModule val module = FakeSwitchModule()
 *
 *   @Inject lateinit var switch: AsyncSwitch
 *
 *   @Test
 *   fun test() {
 *     (switch as FakeSwitch).enabledKeys.add("my-feature")
 *     // test behavior
 *   }
 * }
 * ```
 */
class FakeSwitchModule : KAbstractModule() {
  override fun configure() {
    bind<FakeSwitch>().asSingleton()
    bind<Switch>().to<FakeSwitch>()
    bind<AsyncSwitch>().to<FakeSwitch>()
    multibind<TestFixture>().to<FakeSwitch>()
  }
}

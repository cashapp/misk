package misk.inject

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.FakeFixture

/**
 * A fake implementation of [Switch] for testing that allows manual toggling of switch states.
 *
 * This is useful for tests that need to verify behavior with switches enabled and disabled without requiring complex
 * configuration. The switch state can be easily changed during test execution by modifying [enabledKeys].
 *
 * Example usage:
 * ```kotlin
 * @MiskTest
 * class MyFeatureTest {
 *   @MiskTestModule val module = object : KAbstractModule() {
 *     override fun configure() {
 *       bind<MyFeatureSwitch>().to<FakeSwitch>()
 *     }
 *   }
 *
 *   @Inject lateinit var featureSwitch: MyFeatureSwitch
 *
 *   @Test
 *   fun testFeatureEnabled() {
 *     (featureSwitch as FakeSwitch).enabledKeys.add("my-feature")
 *     // test enabled behavior
 *   }
 *
 *   @Test
 *   fun testFeatureDisabled() {
 *     (featureSwitch as FakeSwitch).enabledKeys.remove("my-feature")
 *     // test disabled behavior
 *   }
 * }
 * ```
 */
@Singleton
class FakeSwitch @Inject constructor() : FakeFixture(), Switch, AsyncSwitch {
  val enabledKeys by resettable { mutableSetOf<String>() }

  override fun isEnabled(key: String): Boolean = enabledKeys.contains(key)
}

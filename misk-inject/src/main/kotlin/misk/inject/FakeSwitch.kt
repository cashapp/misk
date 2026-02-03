package misk.inject

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.FakeFixture

/**
 * A fake implementation of [Switch] for testing that allows manual toggling of switch states.
 *
 * This is useful for tests that need to verify behavior with switches enabled and disabled without requiring complex
 * configuration. The switch state can be easily changed during test execution.
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
 *     (featureSwitch as FakeSwitch).enable("my-feature")
 *     // test enabled behavior
 *   }
 *
 *   @Test
 *   fun testFeatureDisabled() {
 *     (featureSwitch as FakeSwitch).disable("my-feature")
 *     // test disabled behavior
 *   }
 * }
 * ```
 */
@Singleton
class FakeSwitch @Inject constructor() : FakeFixture(), Switch, AsyncSwitch {
  private val _enabledKeys by resettable { mutableSetOf<String>() }

  override fun isEnabled(key: String): Boolean = _enabledKeys.contains(key)

  /** Enables the switch for the given key. */
  fun enable(key: String) {
    _enabledKeys.add(key)
  }

  /** Disables the switch for the given key. */
  fun disable(key: String) {
    _enabledKeys.remove(key)
  }

  /** Enables all provided keys. */
  fun enableAll(vararg keys: String) {
    _enabledKeys.addAll(keys)
  }

  /** Disables all provided keys. */
  fun disableAll(vararg keys: String) {
    _enabledKeys.removeAll(keys.toSet())
  }

  /** Toggles the switch state for the given key. */
  fun toggle(key: String) {
    if (isEnabled(key)) {
      disable(key)
    } else {
      enable(key)
    }
  }

  /** Returns all currently enabled keys. */
  fun getEnabledKeys(): Set<String> = _enabledKeys.toSet()

  /** Clears all enabled keys. */
  fun clear() {
    _enabledKeys.clear()
  }
}

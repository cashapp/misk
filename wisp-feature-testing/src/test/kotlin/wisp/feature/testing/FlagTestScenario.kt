package wisp.feature.testing

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import wisp.feature.FeatureFlag
import wisp.feature.TrackerReference
import java.util.concurrent.Executor

/**
 * Helper function that lets us write tests for all feature flag types, instead of duplcating
 * tests 6 times.
 *
 * Within a test [FlagTestScenario] is in scope, providing access to `scenarioOverride`,
 * `scenarioGet`, `scenarioTrack` etc...
 *
 * Usage:
 *
 * ```kotlin
 * @TestFactory
 * fun `my test for all flags`() = forAllFlagTypes {
 *   // All methods of
 *
 *   val featureFlags = FakeFeatureFlags()
 *     .scenarioOverride(scenarioValueOne) // calls `.override<TestFlagX>(scenarioValueOne)
 *
 *   // Calls `.get<TestFlagX>()`
 *   featureFlags.scenarioGet(scenarioFlag).shouldBe(scenarioValueOne)
 * }
 * ```
 */
@Suppress("UNCHECKED_CAST")
fun forAllStrongFlagTypes(
  block: FlagTestScenario<FeatureFlag<Any>, Any>.() -> Unit
): List<DynamicTest> = listOf(
  StringFlagTestScenario(block as FlagTestScenario<TestStringFlag, String>.() -> Unit),
  BooleanFlagTestScenario(block as FlagTestScenario<TestBooleanFlag, Boolean>.() -> Unit),
  IntFlagTestScenario(block as FlagTestScenario<TestIntFlag, Int>.() -> Unit),
  DoubleFlagTestScenario(block as FlagTestScenario<TestDoubleFlag, Double>.() -> Unit),
  EnumFlagTestScenario(block as FlagTestScenario<TestEnumFlag, TestEnum>.() -> Unit),
  JsonFlagTestScenario(block as FlagTestScenario<TestJsonFlag, TestJsonObject>.() -> Unit),
)

interface FlagTestScenario<Flag : FeatureFlag<in T>, T : Any> {
  /**
   * The name of the scenario, typically "string flag" or "boolean flag"
   */
  val scenarioName: String

  /**
   * A value of type [T], distinct from [scenarioValueTwo]
   */
  val scenarioValueOne: T

  /**
   * A value of type [T], distinct from [scenarioValueOne]
   */
  val scenarioValueTwo: T

  /**
   * Create a feature flag of type [Flag]. Always one of [TestFlag]
   */
  fun scenarioFlag(
    username: String = "test username ($scenarioName)",
    segment: String = "test segment ($scenarioName)"
  ): Flag

  /**
   * Call the appropriate strongly typed [FakeFeatureFlags.get] method depending on the scenario.
   *
   * For example, if we are in [StringFlagTestScenario], this will call call
   * `FakeFeatureFlags.get<TestStringFlag>()`
   */
  fun FakeFeatureFlags.scenarioGet(flag: Flag): T

  /**
   * Call the appropriate strongly typed [FakeFeatureFlags.override] method depending on the
   * scenario.
   *
   * For example, if we are in [StringFlagTestScenario], this will call call
   * `FakeFeatureFlags.override<TestStringFlag>(value, matcher)`
   */
  fun FakeFeatureFlags.scenarioOverride(
    value: T,
    matcher: (TestFlag<T>) -> Boolean = { _ -> true }
  ): FakeFeatureFlags

  operator fun invoke(block: FlagTestScenario<Flag, T>.() -> Unit): DynamicTest =
    dynamicTest(scenarioName) { block(this) }
}

object StringFlagTestScenario : FlagTestScenario<TestStringFlag, String> {
  override val scenarioName = "string flag"
  override val scenarioValueOne: String = "string value 1"
  override val scenarioValueTwo: String = "string value 2"

  override fun scenarioFlag(username: String, segment: String): TestStringFlag =
    TestStringFlag(username, segment)

  override fun FakeFeatureFlags.scenarioGet(flag: TestStringFlag): String = this.get(flag)

  override fun FakeFeatureFlags.scenarioOverride(
    value: String,
    matcher: (TestFlag<String>) -> Boolean
  ): FakeFeatureFlags = this.override<TestStringFlag>(value, matcher)
}

object BooleanFlagTestScenario : FlagTestScenario<TestBooleanFlag, Boolean> {
  override val scenarioName = "boolean flag"
  override val scenarioValueOne = true
  override val scenarioValueTwo = false

  override fun scenarioFlag(username: String, segment: String): TestBooleanFlag =
    TestBooleanFlag(username, segment)

  override fun FakeFeatureFlags.scenarioGet(flag: TestBooleanFlag): Boolean = this.get(flag)

  override fun FakeFeatureFlags.scenarioOverride(
    value: Boolean,
    matcher: (TestFlag<Boolean>) -> Boolean
  ): FakeFeatureFlags = this.override<TestBooleanFlag>(value, matcher)
}

object IntFlagTestScenario : FlagTestScenario<TestIntFlag, Int> {
  override val scenarioName = "int flag"
  override val scenarioValueOne = 0
  override val scenarioValueTwo = 100

  override fun scenarioFlag(username: String, segment: String): TestIntFlag =
    TestIntFlag(username, segment)

  override fun FakeFeatureFlags.scenarioGet(flag: TestIntFlag): Int = this.get(flag)

  override fun FakeFeatureFlags.scenarioOverride(
    value: Int,
    matcher: (TestFlag<Int>) -> Boolean
  ): FakeFeatureFlags = this.override<TestIntFlag>(value, matcher)
}

object DoubleFlagTestScenario : FlagTestScenario<TestDoubleFlag, Double> {
  override val scenarioName = "double flag"
  override val scenarioValueOne = 0.0
  override val scenarioValueTwo = 100.0

  override fun scenarioFlag(username: String, segment: String): TestDoubleFlag =
    TestDoubleFlag(username, segment)

  override fun FakeFeatureFlags.scenarioGet(flag: TestDoubleFlag): Double = this.get(flag)

  override fun FakeFeatureFlags.scenarioOverride(
    value: Double,
    matcher: (TestFlag<Double>) -> Boolean
  ): FakeFeatureFlags = this.override<TestDoubleFlag>(value, matcher)
}

object EnumFlagTestScenario : FlagTestScenario<TestEnumFlag, TestEnum> {
  override val scenarioName = "enum flag (TestEnum)"
  override val scenarioValueOne = TestEnum.TEST_VALUE_1
  override val scenarioValueTwo = TestEnum.TEST_VALUE_2

  override fun scenarioFlag(username: String, segment: String): TestEnumFlag =
    TestEnumFlag(username, segment)

  override fun FakeFeatureFlags.scenarioGet(flag: TestEnumFlag): TestEnum = this.get(flag)

  override fun FakeFeatureFlags.scenarioOverride(
    value: TestEnum,
    matcher: (TestFlag<TestEnum>) -> Boolean
  ): FakeFeatureFlags = this.override<TestEnumFlag, TestEnum>(value, matcher)
}

object JsonFlagTestScenario : FlagTestScenario<TestJsonFlag, TestJsonObject> {
  override val scenarioName = "json flag (TestJsonObject)"
  override val scenarioValueOne = TestJsonObject(field1 = "hello", field2 = 0)
  override val scenarioValueTwo = TestJsonObject(field1 = "goodbye", field2 = 100)

  override fun scenarioFlag(username: String, segment: String): TestJsonFlag =
    TestJsonFlag(username, segment)

  override fun FakeFeatureFlags.scenarioGet(flag: TestJsonFlag): TestJsonObject = this.get(flag)

  override fun FakeFeatureFlags.scenarioOverride(
    value: TestJsonObject,
    matcher: (TestFlag<TestJsonObject>) -> Boolean
  ): FakeFeatureFlags = this.override<TestJsonFlag, TestJsonObject>(value, matcher)
}

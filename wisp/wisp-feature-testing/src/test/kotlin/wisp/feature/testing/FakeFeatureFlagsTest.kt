package wisp.feature.testing

import com.squareup.moshi.JsonDataException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import wisp.config.ConfigSource
import wisp.config.WispConfig
import wisp.config.addWispConfigSources
import wisp.feature.Attributes
import wisp.feature.Feature
import wisp.feature.getEnum
import wisp.feature.getJson

internal class FakeFeatureFlagsTest {
  val FEATURE = Feature("foo")
  val OTHER_FEATURE = Feature("bar")
  val TOKEN = "cust_abcdef123"

  lateinit var subject: FakeFeatureFlags

  @BeforeEach
  fun beforeEachTest() {
    subject = FakeFeatureFlags()
  }

  @Test
  fun getBoolean() {
    // Default throws.
    assertThrows<RuntimeException> { subject.getBoolean(FEATURE, TOKEN) }

    // Can be overridden
    subject.override(FEATURE, true)
    subject.override(OTHER_FEATURE, false)
    assertThat(subject.getBoolean(FEATURE, TOKEN)).isEqualTo(true)
    assertThat(subject.getBoolean(OTHER_FEATURE, TOKEN)).isEqualTo(false)

    // Can override with specific keys
    subject.overrideKey(FEATURE, "joker", false)
    assertThat(subject.getBoolean(FEATURE, TOKEN)).isEqualTo(true)
    assertThat(subject.getBoolean(FEATURE, "joker")).isEqualTo(false)

    // Can override with specific keys and attributes
    val attributes = Attributes(mapOf("type" to "bad"))
    subject.overrideKey(FEATURE, "joker", false, attributes)
    assertThat(subject.getBoolean(FEATURE, TOKEN)).isEqualTo(true)
    assertThat(subject.getBoolean(FEATURE, "joker")).isEqualTo(false)
    assertThat(subject.getBoolean(FEATURE, "joker", attributes)).isEqualTo(false)
    // Provides the key level override when there is no match on attributes
    assertThat(
      subject.getBoolean(
        FEATURE,
        "joker",
        Attributes(mapOf("don't" to "exist"))
      )
    ).isEqualTo(false)
  }

  @Test
  fun getDouble() {
    // Default throws.
    assertThrows<RuntimeException> { subject.getDouble(FEATURE, TOKEN) }

    // Can be overridden
    subject.override(FEATURE, 1.0)
    subject.override(OTHER_FEATURE, 2.0)
    assertThat(subject.getDouble(FEATURE, TOKEN)).isEqualTo(1.0)
    assertThat(subject.getDouble(OTHER_FEATURE, TOKEN)).isEqualTo(2.0)

    // Can override with specific keys
    subject.overrideKey(FEATURE, "joker", 3.0)
    assertThat(subject.getDouble(FEATURE, TOKEN)).isEqualTo(1.0)
    assertThat(subject.getDouble(FEATURE, "joker")).isEqualTo(3.0)

    // Can override with specific keys and attributes
    val attributes = Attributes(mapOf("type" to "bad"))
    subject.overrideKey(FEATURE, "joker", 4.0, attributes)
    assertThat(subject.getDouble(FEATURE, TOKEN)).isEqualTo(1.0)
    assertThat(subject.getDouble(FEATURE, "joker")).isEqualTo(3.0)
    assertThat(subject.getDouble(FEATURE, "joker", attributes)).isEqualTo(4.0)
    // Provides the key level override when there is no match on attributes
    assertThat(
      subject.getDouble(
        FEATURE,
        "joker",
        Attributes(mapOf("don't" to "exist"))
      )
    ).isEqualTo(3.0)
  }

  @Test
  fun getInt() {
    // Default throws.
    assertThrows<RuntimeException> {
      subject.getInt(FEATURE, TOKEN)
    }

    // Can be overridden
    subject.override(FEATURE, 3)
    subject.override(OTHER_FEATURE, 5)
    assertThat(subject.getInt(FEATURE, TOKEN)).isEqualTo(3)
    assertThat(subject.getInt(OTHER_FEATURE, TOKEN)).isEqualTo(5)

    // Can override with specific keys
    subject.overrideKey(FEATURE, "joker", 42)
    assertThat(subject.getInt(FEATURE, TOKEN)).isEqualTo(3)
    assertThat(subject.getInt(FEATURE, "joker")).isEqualTo(42)

    // Can override with specific keys and attributes
    val attributes = Attributes(mapOf("type" to "bad"))
    subject.overrideKey(FEATURE, "joker", 55, attributes)
    assertThat(subject.getInt(FEATURE, TOKEN)).isEqualTo(3)
    assertThat(subject.getInt(FEATURE, "joker")).isEqualTo(42)
    assertThat(subject.getInt(FEATURE, "joker", attributes)).isEqualTo(55)
    // Provides the key level override when there is no match on attributes
    assertThat(
      subject.getInt(
        FEATURE,
        "joker",
        Attributes(mapOf("don't" to "exist"))
      )
    ).isEqualTo(42)
  }

  @Test
  fun getEnum() {
    // Default throws.
    assertThrows<RuntimeException> {
      subject.getEnum<Dinosaur>(FEATURE, TOKEN)
    }

    // Can be overridden
    subject.override(FEATURE, Dinosaur.TYRANNOSAURUS)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
      .isEqualTo(Dinosaur.TYRANNOSAURUS)

    // Can override with specific keys
    subject.overrideKey(FEATURE, "joker", Dinosaur.PTERODACTYL)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
      .isEqualTo(Dinosaur.TYRANNOSAURUS)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, "joker"))
      .isEqualTo(Dinosaur.PTERODACTYL)

    // Can override with specific keys and attributes
    val attributes = Attributes(mapOf("type" to "bad"))
    subject.overrideKey(FEATURE, "joker", Dinosaur.TALARURUS, attributes)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN)).isEqualTo(Dinosaur.TYRANNOSAURUS)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, "joker")).isEqualTo(Dinosaur.PTERODACTYL)
    assertThat(
      subject.getEnum<Dinosaur>(
        FEATURE,
        "joker",
        attributes
      )
    ).isEqualTo(Dinosaur.TALARURUS)
    // Provides the key level override when there is no match on attributes
    assertThat(
      subject.getEnum<Dinosaur>(
        FEATURE, "joker", Attributes(mapOf("don't" to "exist"))
      )
    ).isEqualTo(Dinosaur.PTERODACTYL)

    subject.reset()
    assertThrows<RuntimeException> {
      subject.getEnum<Dinosaur>(FEATURE, TOKEN)
    }
  }

  data class JsonFeature(val value: String, val optional: String? = null)

  @Test
  fun getJson() {
    // Default throws.
    assertThrows<RuntimeException> { subject.getJson<JsonFeature>(FEATURE, TOKEN) }

    // Can be overridden
    subject.overrideJson(FEATURE, JsonFeature("test"))
    subject.overrideJson(OTHER_FEATURE, JsonFeature("other"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, TOKEN)).isEqualTo(JsonFeature("test"))
    assertThat(subject.getJson<JsonFeature>(OTHER_FEATURE, TOKEN)).isEqualTo(JsonFeature("other"))

    // Can override with specific keys
    subject.overrideKeyJson(FEATURE, "joker", JsonFeature("joker"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, TOKEN)).isEqualTo(JsonFeature("test"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, "joker")).isEqualTo(JsonFeature("joker"))

    // Can override with specific attributes
    val goodJokerAttributes = Attributes(mapOf("type" to "good"))
    val badJokerAttributes = Attributes(mapOf("type" to "bad"))
    val sleepyBadJokerAttributes = Attributes(mapOf("type" to "bad", "state" to "sleepy"))
    subject.overrideKeyJson(FEATURE, "joker", JsonFeature("bad-joker"), badJokerAttributes)

    assertThat(subject.getJson<JsonFeature>(FEATURE, TOKEN)).isEqualTo(JsonFeature("test"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, "joker")).isEqualTo(JsonFeature("joker"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, "joker", badJokerAttributes)).isEqualTo(
      JsonFeature("bad-joker")
    )
    assertThat(subject.getJson<JsonFeature>(FEATURE, "joker", sleepyBadJokerAttributes)).isEqualTo(
      JsonFeature("bad-joker")
    )
    // Provides the key level override when there is no match on attributes
    assertThat(subject.getJson<JsonFeature>(FEATURE, "joker", goodJokerAttributes))
      .isEqualTo(JsonFeature("joker"))

    subject.reset()
    subject.override(FEATURE, JsonFeature("test-class"), JsonFeature::class.java)
    subject.overrideKey(
      FEATURE, "joker", JsonFeature("test-key-class"), JsonFeature::class.java
    )
    assertThat(subject.getJson<JsonFeature>(FEATURE))
      .isEqualTo(JsonFeature("test-class"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, "joker"))
      .isEqualTo(JsonFeature("test-key-class"))
  }

  @Test
  fun getString() {
    // Default returns false and not throw as the other variants.
    assertThrows<RuntimeException> { subject.getString(FEATURE, TOKEN) }

    // Can be overridden
    subject.override(FEATURE, "feature")
    subject.override(OTHER_FEATURE, "other-feature")
    assertThat(subject.getString(FEATURE, TOKEN)).isEqualTo("feature")
    assertThat(subject.getString(OTHER_FEATURE, TOKEN)).isEqualTo("other-feature")

    // Can override with specific keys
    subject.overrideKey(FEATURE, "joker", "feature-joker")
    assertThat(subject.getString(FEATURE, TOKEN)).isEqualTo("feature")
    assertThat(subject.getString(FEATURE, "joker")).isEqualTo("feature-joker")

    // Can override with specific keys and attributes
    val attributes = Attributes(mapOf("type" to "bad"))
    subject.overrideKey(FEATURE, "joker", "feature-joker-with-attrs", attributes)
    assertThat(subject.getString(FEATURE, TOKEN)).isEqualTo("feature")
    assertThat(subject.getString(FEATURE, "joker")).isEqualTo("feature-joker")
    assertThat(
      subject.getString(
        FEATURE,
        "joker",
        attributes
      )
    ).isEqualTo("feature-joker-with-attrs")
    // Provides the key level override when there is no match on attributes
    assertThat(
      subject.getString(
        FEATURE,
        "joker",
        Attributes(mapOf("don't" to "exist"))
      )
    ).isEqualTo("feature-joker")
  }

  @Test
  fun `provides the latest override in case two or more attributes are a match`() {
    val typeAttribute = Attributes(mapOf("type" to "bad"))
    subject.overrideKey(FEATURE, "joker", 55, typeAttribute)
    assertThat(subject.getInt(FEATURE, "joker", typeAttribute)).isEqualTo(55)

    val stateAttribute = Attributes(mapOf("state" to "sleepy"))
    subject.overrideKey(FEATURE, "joker", 75, stateAttribute)
    assertThat(subject.getInt(FEATURE, "joker", stateAttribute)).isEqualTo(75)

    val combinedAttributes = Attributes(typeAttribute.text + stateAttribute.text)
    assertThat(subject.getInt(FEATURE, "joker", combinedAttributes)).isEqualTo(75)
  }

  @Test
  fun `skip unknown field in json parsing`() {
    val json = """
      {
        "value" : "dino",
        "unknown_key": "unknown"
      }
    """.trimIndent()
    subject.overrideJsonString(FEATURE, json)
    assertThat(subject.getJson<JsonFeature>(FEATURE, TOKEN)).isEqualTo(JsonFeature("dino"))
  }

  @Test
  fun `fails for missing required fields`() {
    subject.overrideJsonString(
      FEATURE,
      """
      {
        "optional" : "value"
      }
      """.trimIndent()
    )

    assertThrows<JsonDataException> { subject.getJson<JsonFeature>(FEATURE, TOKEN) }
  }

  @Test
  fun invalidKeys() {
    subject.override(FEATURE, 3)
    assertThrows<IllegalArgumentException> {
      subject.getInt(FEATURE, "")
    }
  }

  @Test
  fun validKeys() {
    subject.override(FEATURE, 3)
    subject.getInt(FEATURE, "hello")
    subject.getInt(FEATURE, "09afAF") // hex.
    subject.getInt(FEATURE, "AZ27=") // base32.
    subject.getInt(FEATURE, "azAZ09+/=") // base64.
    subject.getInt(FEATURE, "azAZ09-_=") // base64url.
    subject.getInt(FEATURE, "azAZ09-_.~$") // unreserved URL characters.
  }

  enum class Dinosaur {
    PTERODACTYL,
    TYRANNOSAURUS,
    TALARURUS
  }

  @Test
  fun configureOverridesFeatures() {
    val configSource = ConfigSource("classpath:/featureFlagsConfig.yaml")

    val clazz = subject.getConfigClass()

    val config = WispConfig.builder().addWispConfigSources(listOf(configSource)).build()
      .loadConfigOrThrow(clazz, emptyList())
    subject.configure(config)

    assertThat(subject.getInt(Feature("foo1"))).isEqualTo(1)
    assertThat(subject.getJson<JsonFeature>(Feature("fooJson")).optional).isEqualTo("value")
  }

  /**
   * The heavy-lifying for strong feature flag testing lives in the other `TestFactory`-based
   * tests below, but they use a lot of generic magic to test every type of flag.
   *
   * To make sure we didn't miss anything, we also do a normal smoke test on String, if this works
   * and all the below tests also work we can be reasonably confident things are working well.
   */
  @Test
  fun `string feature flags should act as expected`() {
    // Default throws
    shouldThrow<RuntimeException> { subject.get(TestStringFlag()) }

    // Can be overridden
    subject.override<TestStringFlag>("feature")
    subject.get(TestStringFlag()).shouldBe("feature")

    // Can override with specific keys
    subject.override<TestStringFlag>("feature-joker") { it.username == "joker" }
    subject.get(TestStringFlag()).shouldBe("feature")
    subject.get(TestStringFlag(username = "joker")).shouldBe("feature-joker")

    // Can override with specific keys and attributes
    subject.override<TestStringFlag>("feature-joker-bad") {
      it.username == "joker" && it.segment == "bad"
    }
    subject.get(TestStringFlag()).shouldBe("feature")
    subject.get(TestStringFlag(username = "joker")).shouldBe("feature-joker")
    subject.get(TestStringFlag(username = "joker", segment = "bad")).shouldBe("feature-joker-bad")
  }

  @TestFactory
  fun `strong feature flags should throw if no override is set`() = forAllStrongFlagTypes {
    val featureFlags = FakeFeatureFlags()
    shouldThrow<RuntimeException> { featureFlags.scenarioGet(scenarioFlag()) }
  }

  @TestFactory
  fun `strong feature flags should return overriden value`() = forAllStrongFlagTypes {
    val featureFlags = FakeFeatureFlags()
      .scenarioOverride(scenarioValueOne)

    featureFlags.scenarioGet(scenarioFlag()).shouldBe(scenarioValueOne)
  }

  @TestFactory
  fun `strong feature flags should prefer last override value`() = forAllStrongFlagTypes {
    val featureFlags = FakeFeatureFlags()
      .scenarioOverride(scenarioValueOne)
      .scenarioOverride(scenarioValueTwo)

    featureFlags.scenarioGet(scenarioFlag()).shouldBe(scenarioValueTwo)
  }

  @TestFactory
  fun `strong feature flags should use latest override if condition is met`() = forAllStrongFlagTypes {
    val featureFlags = FakeFeatureFlags()
      .scenarioOverride(scenarioValueOne)
      .scenarioOverride(scenarioValueTwo) { it.username == "vip" }

    featureFlags.scenarioGet(scenarioFlag(username = "vip")).shouldBe(scenarioValueTwo)
  }

  @TestFactory
  fun `strong feature flags should work for non-key attributes`() = forAllStrongFlagTypes {
    val featureFlags = FakeFeatureFlags()
      .scenarioOverride(scenarioValueOne) { it.segment == "pro-segment" }

    featureFlags.scenarioGet(scenarioFlag(segment = "pro-segment")).shouldBe(scenarioValueOne)
  }

  @TestFactory
  fun `strong feature flags should still use older overrides if newer ones don't match`() =
    forAllStrongFlagTypes {
      val featureFlags = FakeFeatureFlags()
        .scenarioOverride(scenarioValueOne)
        .scenarioOverride(scenarioValueTwo) { it.username == "vip" }

      featureFlags.scenarioGet(scenarioFlag(username = "shlub")).shouldBe(scenarioValueOne)
    }

  @TestFactory
  fun `strong feature flags should not match if condition is not met`() = forAllStrongFlagTypes {
    val featureFlags = FakeFeatureFlags()
      .scenarioOverride(scenarioValueOne) { it.username == "vip" }

    shouldThrow<RuntimeException> { featureFlags.scenarioGet(scenarioFlag(username = "shlub")) }
  }
}

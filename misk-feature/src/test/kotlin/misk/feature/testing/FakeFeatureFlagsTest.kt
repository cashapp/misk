package misk.feature.testing

import com.squareup.moshi.JsonDataException
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import misk.feature.Attributes
import misk.feature.Feature
import misk.feature.getEnum
import misk.feature.getJson
import jakarta.inject.Inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@MiskTest
internal class FakeFeatureFlagsTest {
  val FEATURE = Feature("foo")
  val OTHER_FEATURE = Feature("bar")
  val TOKEN = "cust_abcdef123"

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(FakeFeatureFlagsModule())
      install(MoshiTestingModule())
    }
  }

  @MiskTestModule val module = TestModule()

  @Inject lateinit var subject: FakeFeatureFlags

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
  fun `trackString works`() {
    subject.override(FEATURE, "test")
    assertThat(subject.getString(FEATURE, TOKEN)).isEqualTo("test")

    val listenerWasCalled = CountDownLatch(1)
    var valueReceivedByListenerReceived: String? = null

    subject.trackString(FEATURE, Executors.newSingleThreadExecutor()) { newValue ->
      valueReceivedByListenerReceived = newValue
      listenerWasCalled.countDown()
    }

    // override the featureflag, this should trigger trackString
    subject.override(FEATURE, "newValue")
    listenerWasCalled.await(1, TimeUnit.SECONDS)

    assertThat(valueReceivedByListenerReceived).isEqualTo("newValue")
  }

  @Test
  fun `trackJson works`() {
    subject.overrideJson(FEATURE, JsonFeature("test"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, TOKEN)).isEqualTo(JsonFeature("test"))

    val listenerWasCalled = CountDownLatch(1)
    var valueReceivedByListenerReceived: JsonFeature? = null

    subject.trackJson(FEATURE, JsonFeature::class.java, Executors.newSingleThreadExecutor())  { newValue ->
      valueReceivedByListenerReceived = newValue
      listenerWasCalled.countDown()
    }

    // override the featureflag, this should trigger trackJson
    subject.overrideJson(FEATURE, JsonFeature("newValue", optional = "some"))
    listenerWasCalled.await(1, TimeUnit.SECONDS)

    assertThat(valueReceivedByListenerReceived).isEqualTo(JsonFeature("newValue", optional = "some"))
  }

}

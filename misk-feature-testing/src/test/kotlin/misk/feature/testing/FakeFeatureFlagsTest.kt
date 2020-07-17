package misk.feature.testing

import com.squareup.moshi.JsonDataException
import misk.feature.Feature
import misk.feature.getEnum
import misk.feature.getJson
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject

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
  }

  @Test
  fun getEnum() {
    // Defaults to first enum.
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
        .isEqualTo(Dinosaur.PTERODACTYL)

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

    subject.reset()
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
        .isEqualTo(Dinosaur.PTERODACTYL)
  }

  data class JsonFeature(val value : String, val optional : String? = null)

  @Test
  fun getJson() {
    // Default throws.
    assertThrows<RuntimeException> { subject.getJson<JsonFeature>(FEATURE, TOKEN) }

    // Can be overridden
    subject.overrideJson(FEATURE, JsonFeature("test"))
    subject.overrideJson(OTHER_FEATURE, JsonFeature("other"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, TOKEN)).isEqualTo(JsonFeature("test"))
    assertThat(subject.getJson<JsonFeature>(OTHER_FEATURE, TOKEN))
        .isEqualTo(JsonFeature("other"))

    // Can override with specific keys
    subject.overrideKeyJson(FEATURE, "joker", JsonFeature("joker"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, TOKEN)).isEqualTo(JsonFeature("test"))
    assertThat(subject.getJson<JsonFeature>(FEATURE, "joker")).isEqualTo(JsonFeature("joker"))
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
    subject.overrideJsonString(FEATURE, """
      {
        "optional" : "value"
      }
    """.trimIndent())

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
    TYRANNOSAURUS
  }
}

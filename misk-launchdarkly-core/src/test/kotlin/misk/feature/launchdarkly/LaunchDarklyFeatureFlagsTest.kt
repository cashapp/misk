package misk.feature.launchdarkly

import ch.qos.logback.classic.Level
import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.UserAttribute
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import misk.feature.Attributes
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.getEnum
import misk.feature.getJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import misk.logging.QueuedLogCollector
import wisp.moshi.defaultKotlinMoshi
import java.util.function.Function
import wisp.launchdarkly.LaunchDarklyFeatureFlags as WispLaunchDarklyFeatureFlags

internal class LaunchDarklyFeatureFlagsTest {
  private val client = mock(LDClientInterface::class.java)
  private val moshi = defaultKotlinMoshi
  private val wispLaunchDarklyFeatureFlags = wisp.launchdarkly.LaunchDarklyFeatureFlags(client, moshi)
  private val featureFlags: FeatureFlags = LaunchDarklyFeatureFlags(wispLaunchDarklyFeatureFlags)
  private val logCollector = QueuedLogCollector()

  @BeforeEach
  fun beforeEach() {
    Mockito.`when`(client.isInitialized).thenReturn(true)
    logCollector.startUp()
  }

  @AfterEach
  fun afterEach() {
    logCollector.shutDown()
  }

  @Test
  fun getEnum() {
    Mockito
      .`when`(client.stringVariationDetail(anyString(), any(LDContext::class.java), anyString()))
      .thenReturn(
        EvaluationDetail.fromValue(
          "TYRANNOSAURUS", 1, EvaluationReason.targetMatch()
        )
      )

    val feature = featureFlags.getEnum<Dinosaur>(
      Feature("which-dinosaur"), "abcd",
      Attributes(
        mapOf(
          "continent" to "europa",
          "platform" to "lava"
        ),
        mapOf(
          "age" to 100000
        )
      )
    )

    assertThat(feature).isEqualTo(Dinosaur.TYRANNOSAURUS)

    val userCaptor = ArgumentCaptor.forClass(LDContext::class.java)
    verify(client, times(1))
      .stringVariationDetail(eq("which-dinosaur"), userCaptor.capture(), eq(""))

    val user = userCaptor.value

    // User fields are package-private, so we fetch it with reflection magicks!
    val attributesField = LDContext::class.java.getDeclaredField("attributes")
    attributesField.isAccessible = true
    val customAttrs = attributesField.get(user)

    val privateAttrsField = LDContext::class.java.getDeclaredField("privateAttributes")
    privateAttrsField.isAccessible = true
    @Suppress("unchecked_cast")
    val privateAttrs = privateAttrsField.get(user) as List<String>
    val continent = UserAttribute.forName("continent")
    val platform = UserAttribute.forName("platform")
    val age = UserAttribute.forName("age")

    val getMethod = Class.forName("com.launchdarkly.sdk.AttributeMap").getDeclaredMethod("get", String::class.java)
    getMethod.isAccessible = true
    assertThat((getMethod.invoke(customAttrs, "continent") as LDValue).stringValue()).isEqualTo("europa")
    assertThat((getMethod.invoke(customAttrs, "platform") as LDValue).stringValue()).isEqualTo("lava")
    assertThat((getMethod.invoke(customAttrs, "age") as LDValue).intValue()).isEqualTo(100000)
    assertThat(privateAttrs.toSet().equals(setOf(continent, platform, age)))
  }

  @Test
  fun getEnumThrowsOnDefault() {
    Mockito
      .`when`(client.stringVariationDetail(anyString(), any(LDContext::class.java), anyString()))
      .thenReturn(
        EvaluationDetail.fromValue(
          "PTERODACTYL",
          EvaluationDetail.NO_VARIATION,
          EvaluationReason.off()
        )
      )

    assertThrows<IllegalStateException> {
      featureFlags.getEnum<Dinosaur>(
        Feature("which-dinosaur"), "a-token"
      )
    }
  }

  @Test
  fun getEnumThrowsOnEvalError() {
    Mockito
      .`when`(client.stringVariationDetail(anyString(), any(LDContext::class.java), anyString()))
      .thenReturn(
        EvaluationDetail.fromValue(
          "PTERODACTYL",
          EvaluationDetail.NO_VARIATION,
          EvaluationReason.error(EvaluationReason.ErrorKind.MALFORMED_FLAG)
        )
      )

    assertThrows<RuntimeException> {
      featureFlags.getEnum<Dinosaur>(
        Feature("which-dinosaur"), "a-token"
      )
    }
  }

  data class JsonFeature(val value: String)

  @Test
  fun getJson() {
    val json = """{
      "value" : "dino"
    }
    """.trimIndent()
    Mockito
      .`when`(
        client.jsonValueVariationDetail(
          anyString(), any(LDContext::class.java),
          any(LDValue::class.java)
        )
      )
      .thenReturn(
        EvaluationDetail.fromValue(
          LDValue.parse(json), 1, EvaluationReason.targetMatch()
        )
      )

    val feature = featureFlags.getJson<JsonFeature>(Feature("which-dinosaur"), "abcd")

    assertThat(feature).isEqualTo(JsonFeature("dino"))
  }

  @Test
  fun getJsonWithUnknownFieldsLogsOnce() {
    val json = """{
      "value" : "dino",
      "new_field": "more dinos"
    }
    """.trimIndent()
    Mockito
      .`when`(
        client.jsonValueVariationDetail(
          anyString(), any(LDContext::class.java),
          any(LDValue::class.java)
        )
      )
      .thenReturn(
        EvaluationDetail.fromValue(
          LDValue.parse(json), 1, EvaluationReason.targetMatch()
        )
      )

    val feature = featureFlags.getJson<JsonFeature>(Feature("which-dinosaur"), "abcd")

    // Unknown fields are ignored. A log entry occurred. This will be asserted on later.
    assertThat(feature).isEqualTo(JsonFeature("dino"))

    // Eval again, to check later that the warning was logged once.
    featureFlags.getJson<JsonFeature>(Feature("which-dinosaur"), "abcd")

    // Check log events.
    val logEvents = logCollector.takeEvents(WispLaunchDarklyFeatureFlags::class)
    assertThat(logEvents).extracting(Function { it.level }).containsExactly(Level.WARN)
    assertThat(logEvents).extracting(Function { it.message })
      .containsExactly(
        "failed to parse JSON due to unknown fields. ignoring those fields and trying again"
      )
  }

  @Test
  fun invalidKeys() {
    assertThrows<IllegalArgumentException> {
      featureFlags.getEnum<Dinosaur>(Feature("which-dinosaur"), "")
    }
  }

  enum class Dinosaur {
    PTERODACTYL,
    TYRANNOSAURUS
  }

  @Test
  fun attributes() {
    Mockito
      .`when`(client.stringVariationDetail(anyString(), any(LDContext::class.java), anyString()))
      .thenReturn(
        EvaluationDetail.fromValue(
          "value",
          1,
          EvaluationReason.targetMatch()
        )
      )

    val attributes = Attributes(
      mapOf(
        "secondary" to "secondary value",
        "ip" to "127.0.0.1",
        "email" to "email@value.com",
        "name" to "name value",
        "avatar" to "avatar value",
        "firstName" to "firstName value",
        "lastName" to "lastName value",
        "country" to "US",
        "custom1" to "custom1 value",
        "custom2" to "custom2 value"
      )
    )
    val feature = featureFlags.getString(Feature("key"), "user", attributes)
    assertThat(feature).isEqualTo("value")

    val userCaptor = ArgumentCaptor.forClass(LDContext::class.java)
    verify(client, times(1))
      .stringVariationDetail(eq("key"), userCaptor.capture(), eq(""))

    val user = userCaptor.value
    // NB: LDContext properties are package-local so we can't read them here.
    // Create expected user and compare against actual.
    val expected = LDContext.builder("user")
      .name("name value")
      .set("ip", "127.0.0.1")
      .set("email", "email@value.com")
      .set("avatar", "avatar value")
      .set("firstName", "firstName value")
      .set("lastName", "lastName value")
      .set("country", "US")
      .set("secondary", "secondary value").privateAttributes("secondary")
      .set("custom1", "custom1 value").privateAttributes("custom1")
      .set("custom2", "custom2 value").privateAttributes("custom2")
      .build()

    assertThat(user).isEqualTo(expected)
  }
}

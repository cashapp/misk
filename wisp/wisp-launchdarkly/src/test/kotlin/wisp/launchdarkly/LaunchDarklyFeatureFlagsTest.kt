package wisp.launchdarkly

import com.launchdarkly.sdk.*
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.*
import wisp.feature.*
import wisp.moshi.defaultKotlinMoshi

internal class LaunchDarklyFeatureFlagsTest {
    private val client = mock(LDClientInterface::class.java)
    private val moshi = defaultKotlinMoshi
    private val featureFlags: FeatureFlags = LaunchDarklyFeatureFlags(client, moshi)

    @BeforeEach
    fun beforeEach() {
        Mockito.`when`(client.isInitialized).thenReturn(true)
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
        val customField = LDContext::class.java.getDeclaredField("attributes")
        customField.isAccessible = true
        @Suppress("unchecked_cast")
        val customAttrs = customField.get(user) as Map<String, LDValue>

        val privateAttrsField = LDContext::class.java.getDeclaredField("privateAttributes")
        privateAttrsField.isAccessible = true
        @Suppress("unchecked_cast")
        val privateAttrs = privateAttrsField.get(user) as List<String>
        val continent = UserAttribute.forName("continent")
        val platform = UserAttribute.forName("platform")
        val age = UserAttribute.forName("age")

        assertThat(customAttrs.getValue("continent").stringValue()).isEqualTo("europa")
        assertThat(customAttrs.getValue("platform").stringValue()).isEqualTo("lava")
        assertThat(customAttrs.getValue("age").intValue()).isEqualTo(100000)
        assertThat(privateAttrs.toSet().equals(setOf(continent, platform, age)))
    }

    @Test
    fun getEnumThrowsOnDefault() {
        Mockito.`when`(client.isInitialized).thenReturn(false)
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
    fun getJsonString() {
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
      val feature = featureFlags.getJsonString(Feature("which-dinosaur"), "abcd")

      assertThat(feature).isEqualTo("""{"value":"dino"}""")
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

package misk.feature.launchdarkly

import com.google.gson.JsonElement
import com.launchdarkly.client.EvaluationDetail
import com.launchdarkly.client.EvaluationReason
import com.launchdarkly.client.LDClientInterface
import com.launchdarkly.client.LDUser
import misk.feature.Attributes
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.getEnum
import org.assertj.core.api.Assertions.assertThat
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

internal class LaunchDarklyFeatureFlagsTest {
  private val client = mock(LDClientInterface::class.java)
  private val featureFlags: FeatureFlags =
      LaunchDarklyFeatureFlags(client)

  @BeforeEach
  fun beforeEach() {
    Mockito.`when`(client.initialized()).thenReturn(true)
  }

  @Test
  fun getEnum() {
    Mockito
        .`when`(client.stringVariationDetail(anyString(), any(LDUser::class.java), anyString()))
        .thenReturn(EvaluationDetail(EvaluationReason.targetMatch(), 1, "TYRANNOSAURUS"))

    val feature = featureFlags.getEnum<Dinosaur>(
        Feature("which-dinosaur"), "abcd",
        Attributes(mapOf(
            "continent" to "europa",
            "platform" to "lava"
        ), mapOf(
            "age" to 100000
        )))

    assertThat(feature).isEqualTo(
        Dinosaur.TYRANNOSAURUS)

    val userCaptor = ArgumentCaptor.forClass(LDUser::class.java)
    verify(client, times(1))
        .stringVariationDetail(eq("which-dinosaur"), userCaptor.capture(), eq(""))

    val user = userCaptor.value

    // User fields are package-private, so we fetch it with reflection magicks!
    val customField = LDUser::class.java.getDeclaredField("custom")
    customField.isAccessible = true
    @Suppress("unchecked_cast")
    val customAttrs = customField.get(user) as Map<String, JsonElement>

    val privateAttrsField = LDUser::class.java.getDeclaredField("privateAttributeNames")
    privateAttrsField.isAccessible = true
    @Suppress("unchecked_cast")
    val privateAttrs = privateAttrsField.get(user) as Set<String>

    assertThat(customAttrs.getValue("continent").asString).isEqualTo("europa")
    assertThat(customAttrs.getValue("platform").asString).isEqualTo("lava")
    assertThat(customAttrs.getValue("age").asNumber).isEqualTo(100000)
    assertThat(privateAttrs).isEqualTo(setOf("continent", "platform", "age"))
  }

  @Test
  fun getEnumThrowsOnDefault() {
    Mockito
        .`when`(client.stringVariationDetail(anyString(), any(LDUser::class.java), anyString()))
        .thenReturn(EvaluationDetail(EvaluationReason.off(), null, "PTERODACTYL"))

    assertThrows<IllegalStateException> {
      featureFlags.getEnum<Dinosaur>(
          Feature("which-dinosaur"), "a-token")
    }
  }

  @Test
  fun getEnumThrowsOnEvalError() {
    Mockito
        .`when`(client.stringVariationDetail(anyString(), any(LDUser::class.java), anyString()))
        .thenReturn(EvaluationDetail(
            EvaluationReason.error(EvaluationReason.ErrorKind.MALFORMED_FLAG),
            null,
            "PTERODACTYL"))

    assertThrows<RuntimeException> {
      featureFlags.getEnum<Dinosaur>(
          Feature("which-dinosaur"), "a-token")
    }
  }

  enum class Dinosaur {
    PTERODACTYL,
    TYRANNOSAURUS
  }
}
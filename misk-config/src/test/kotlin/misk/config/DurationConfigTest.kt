package misk.config

import java.io.File
import java.time.Duration
import kotlin.test.assertFailsWith
import misk.resources.ResourceLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import wisp.deployment.TESTING

class DurationConfigTest {
  @TempDir lateinit var directory: File

  @ParameterizedTest
  @ValueSource(strings = ["25", "0.025", "0", "-25", "\"25\"", "\"0.025\""])
  fun rejectsUnitlessDurations(value: String) {
    val error = assertFailsWith<IllegalStateException> { load("nested:\n  interval: $value") }
    assertThat(error.message)
      .contains("nested.interval", "ISO-8601", "PT0.025S", "PT25S")
      .doesNotContain("could not find")
  }

  @ParameterizedTest
  @ValueSource(strings = ["PT0.025S", "PT25S", "PT0S", "PT-1S", "P1D", "PT0.000000001S"])
  fun acceptsExplicitDurations(value: String) {
    assertThat(load("nested:\n  interval: $value").nested.interval).isEqualTo(Duration.parse(value))
  }

  @Test
  fun preservesDefaultsNullsAndPrimitiveNumbers() {
    val config = load("nested: {}\noptional: null\ncount: 25")
    assertThat(config.nested.interval).isEqualTo(Duration.ofMillis(1))
    assertThat(config.optional).isNull()
    assertThat(config.count).isEqualTo(25L)
  }

  @Test
  fun redactedYamlRoundTripsWithExplicitUnits() {
    val config = TestConfig(NestedConfig(Duration.ofMillis(25)))
    val yaml = MiskConfig.toRedactedYaml(config, ResourceLoader.SYSTEM)
    assertThat(yaml).contains("PT0.025S")
    assertThat(load(yaml)).isEqualTo(config)
  }

  @ParameterizedTest
  @ValueSource(strings = ["\"\"", "25ms", "true", "[25]", "{}"])
  fun rejectsInvalidDurationShapes(value: String) {
    val error = assertFailsWith<IllegalStateException> { load("nested:\n  interval: $value") }
    assertThat(error.message).contains("nested.interval", "ISO-8601").doesNotContain("could not find")
  }

  @Test
  fun checksDurationsInsideCollections() {
    val error = assertFailsWith<IllegalStateException> { load("nested: {}\ndelays:\n  retry: [PT0.025S, 25]") }
    assertThat(error.message).contains("delays.retry.1", "ISO-8601")
  }

  @Test
  fun validatesTheMergedConfig() {
    val common = File(directory, "common.yaml").apply { writeText("nested:\n  interval: 25") }
    val environment = File(directory, "environment.yaml").apply { writeText("nested:\n  interval: PT0.025S") }
    val config =
      MiskConfig.load<TestConfig>(
        "duration_config",
        TESTING,
        requireExplicitDurationUnits = true,
        overrideFiles = listOf(common, environment),
      )
    assertThat(config.nested.interval).isEqualTo(Duration.ofMillis(25))

    val error =
      assertFailsWith<IllegalStateException> {
        MiskConfig.load<TestConfig>(
          "duration_config",
          TESTING,
          requireExplicitDurationUnits = true,
          overrideFiles = listOf(environment, common),
        )
      }
    assertThat(error.message).contains("nested.interval", "ISO-8601")
  }

  @Test
  fun doesNotChangeOtherJacksonMappers() {
    assertThat(JsonMapper.builder().build().readValue("25", Duration::class.java)).isEqualTo(Duration.ofSeconds(25))
  }

  @ParameterizedTest
  @ValueSource(strings = ["25", "0.025", "0", "-25"])
  fun preservesNumericDurationsUnlessOptedIn(value: String) {
    val file = File(directory, "duration.yaml").apply { writeText("nested:\n  interval: $value") }
    val expected = Duration.parse("PT${value}S")
    val defaultConfig = MiskConfig.load<TestConfig>("duration_config", TESTING, overrideFiles = listOf(file))
    val explicitlyDisabled =
      MiskConfig.load<TestConfig>(
        "duration_config",
        TESTING,
        requireExplicitDurationUnits = false,
        overrideFiles = listOf(file),
      )
    assertThat(defaultConfig.nested.interval).isEqualTo(expected)
    assertThat(explicitlyDisabled).isEqualTo(defaultConfig)
  }

  @ParameterizedTest
  @ValueSource(strings = ["\"25\"", "\"0.025\""])
  fun preservesDefaultRejectionOfQuotedNumbers(value: String) {
    val file = File(directory, "duration.yaml").apply { writeText("nested:\n  interval: $value") }
    val defaultError =
      assertFailsWith<IllegalStateException> {
        MiskConfig.load<TestConfig>("duration_config", TESTING, overrideFiles = listOf(file))
      }
    val disabledError =
      assertFailsWith<IllegalStateException> {
        MiskConfig.load<TestConfig>(
          "duration_config",
          TESTING,
          requireExplicitDurationUnits = false,
          overrideFiles = listOf(file),
        )
      }
    assertThat(disabledError.message).isEqualTo(defaultError.message)
    assertThat(defaultError.message)
      .contains("nested.interval", "Cannot deserialize")
      .doesNotContain("requires an ISO-8601")
  }

  @Test
  fun optInDoesNotLeakBetweenLoads() {
    val file = File(directory, "duration.yaml").apply { writeText("nested:\n  interval: 25") }
    assertFailsWith<IllegalStateException> { load("nested:\n  interval: 25") }
    assertThat(MiskConfig.load<TestConfig>("duration_config", TESTING, overrideFiles = listOf(file)).nested.interval)
      .isEqualTo(Duration.ofSeconds(25))
  }

  @Test
  fun strictnessSurvivesUnknownPropertyFallback() {
    val error = assertFailsWith<IllegalStateException> { load("unknown: true\nnested:\n  interval: 25") }
    assertThat(error.message).contains("nested.interval", "ISO-8601")
  }

  private fun load(yaml: String): TestConfig {
    val file = File(directory, "duration.yaml").apply { writeText(yaml) }
    return MiskConfig.load<TestConfig>(
      "duration_config",
      TESTING,
      requireExplicitDurationUnits = true,
      overrideFiles = listOf(file),
    )
  }

  data class TestConfig(
    val nested: NestedConfig,
    val optional: Duration? = null,
    val count: Long = 25,
    val delays: Map<String, List<Duration>> = emptyMap(),
  ) : Config

  data class NestedConfig(val interval: Duration = Duration.ofMillis(1))
}

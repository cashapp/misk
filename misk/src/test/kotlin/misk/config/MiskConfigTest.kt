package misk.config

import com.google.inject.util.Modules
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.assertThrows
import misk.web.WebConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Named

@MiskTest
class MiskConfigTest {
  val defaultEnv = Environment.TESTING
  val config = MiskConfig.load<TestConfig>("test_app", defaultEnv)

  @MiskTestModule
  val module = Modules.combine(
      ConfigModule.create("test_app", config),
      EnvironmentModule(defaultEnv)
      // @TODO(jwilson) https://github.com/square/misk/issues/272
  )

  @Inject
  private lateinit var testConfig: TestConfig

  @field:[Inject Named("consumer_a")]
  lateinit var consumerA: ConsumerConfig

  @field:[Inject Named("consumer_b")]
  lateinit var consumerB: ConsumerConfig

  @field:[Inject]
  lateinit var webConfig: WebConfig

  @Test
  fun configIsProperlyParsed() {
    assertThat(testConfig.web).isEqualTo(WebConfig(5678, 30_000))
    assertThat(testConfig.consumer_a).isEqualTo(ConsumerConfig(0, 1))
    assertThat(testConfig.consumer_b).isEqualTo(ConsumerConfig(1, 2))
    assertThat(testConfig.duration).isEqualTo(DurationConfig(Duration.parse("PT1S")))
    assertThat((testConfig.action_exception_log_level)).isEqualTo(
        ActionExceptionLogLevelConfig(Level.INFO, Level.ERROR)
    )
  }

  @Test
  fun subConfigsWithDefaultNamesAreBoundUnqualified() {
    assertThat(webConfig).isEqualTo(WebConfig(5678, 30_000))
  }

  @Test
  fun subConfigsWithCustomNamesAreBoundWithNamedQualifiers() {
    assertThat(consumerA).isEqualTo(ConsumerConfig(0, 1))
    assertThat(consumerB).isEqualTo(ConsumerConfig(1, 2))
  }

  @Test
  fun environmentConfigOverridesCommon() {
    assertThat(testConfig.web.port).isEqualTo(5678)
  }

  @Test
  fun defaultValuesAreUsed() {
    assertThat(testConfig.consumer_a.min_items).isEqualTo(0)
  }

  @Test
  fun friendlyErrorMessagesWhenFilesNotFound() {
    val exception = assertThrows<IllegalStateException> {
      MiskConfig.load<TestConfig>(TestConfig::class.java, "missing", defaultEnv)
    }

    assertThat(exception).hasMessageContaining("could not find configuration files -" +
        " checked [missing-common.yaml, missing-testing.yaml]")
  }

  @Test
  fun friendlyErrorMessageWhenConfigPropertyMissing() {
    val exception = assertThrows<IllegalStateException> {
      MiskConfig.load<TestConfig>(TestConfig::class.java, "partial_test_app", defaultEnv)
    }

    assertThat(exception).hasMessageContaining(
        "could not find partial_test_app TESTING configuration for consumer_a")
  }

  @Test
  fun friendlyErrorMessagesWhenFileUnparseable() {
    val exception = assertThrows<IllegalStateException> {
      MiskConfig.load<TestConfig>(TestConfig::class.java, "unparsable", defaultEnv)
    }

    assertThat(exception).hasMessageContaining("could not parse unparsable-common.yaml")
  }

  @Test
  fun friendlyErrorMessagesWhenPropertiesNotFound() {
    val exception = assertThrows<IllegalStateException> {
      MiskConfig.load<TestConfig>(TestConfig::class.java, "unknownproperty", defaultEnv)
    }

    assertThat(exception.cause).hasMessageContaining("Unrecognized field \"blue_items\"")
  }

  @Test
  fun mergesExternalFiles() {
    val overrides = listOf(
        MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml"),
        MiskConfigTest::class.java.getResource("/overrides/override-test-app2.yaml"))
        .map { File(it.file) }

    val config = MiskConfig.load<TestConfig>("test_app", defaultEnv, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 1))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 79))
  }

  @Test
  fun findsFilesInDir() {
    val dir = MiskConfigTest::class.java.getResource("/overrides").file
    val filesInDir = MiskConfig.filesInDir(dir).map { it.absolutePath }
    assertThat(filesInDir).hasSize(2)

    // NB(mmihic): These are absolute paths, so we can only look at the end which is consistent
    assertThat(filesInDir[0]).endsWith("/overrides/override-test-app1.yaml")
    assertThat(filesInDir[1]).endsWith("/overrides/override-test-app2.yaml")
  }
}

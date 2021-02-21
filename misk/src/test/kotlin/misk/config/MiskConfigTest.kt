package misk.config

import com.google.inject.util.Modules
import misk.environment.DeploymentModule
import misk.environment.Environment
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import java.io.File
import java.time.Duration
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
class MiskConfigTest {
  val defaultEnv = Environment.TESTING
  val config = MiskConfig.load<TestConfig>("test_app", defaultEnv)

  @MiskTestModule
  val module = Modules.combine(
    ConfigModule.create("test_app", config),
    DeploymentModule.forTesting()
    // @TODO(jwilson) https://github.com/square/misk/issues/272
  )

  @Inject
  private lateinit var testConfig: TestConfig

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
  fun environmentConfigOverridesCommon() {
    assertThat(testConfig.web.port).isEqualTo(5678)
  }

  @Test
  fun defaultValuesAreUsed() {
    assertThat(testConfig.consumer_a.min_items).isEqualTo(0)
  }

  @Test
  fun friendlyErrorMessagesWhenFilesNotFound() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("missing", defaultEnv)
    }

    assertThat(exception).hasMessageContaining(
      "could not find configuration files -" +
        " checked [classpath:/missing-common.yaml, classpath:/missing-testing.yaml]"
    )
  }

  @Test
  fun friendlyErrorMessageWhenConfigPropertyMissing() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("partial_test_app", defaultEnv)
    }

    assertThat(exception).hasMessageContaining(
      "could not find 'consumer_a' of 'TestConfig' in partial_test_app-testing.yaml"
    )
  }

  @Test
  fun friendlyErrorMessagesWhenFileUnparseable() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("unparsable", defaultEnv)
    }

    assertThat(exception).hasMessageContaining("could not parse classpath:/unparsable-common.yaml")
  }

  @Test
  fun friendlyErrorMessagesWhenPropertiesNotFound() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("unknownproperty", defaultEnv)
    }

    assertThat(exception).hasMessageContaining("'consumer_b.blue_items' not found")
  }

  @Test
  fun friendlyErrorMessagesWhenPropertiesMisspelled() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("misspelledproperty", defaultEnv)
    }

    assertThat(exception).hasMessageContaining("Did you mean")
  }

  @Test
  fun mergesExternalFiles() {
    val overrides = listOf(
      MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml"),
      MiskConfigTest::class.java.getResource("/overrides/override-test-app2.yaml")
    )
      .map { File(it.file) }

    val config = MiskConfig.load<TestConfig>("test_app", defaultEnv, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 122))
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

  @Test
  fun handlesDuplicateNamedExternalFiles() {
    val overrides = listOf(
      MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml"),
      MiskConfigTest::class.java.getResource("/additional_overrides/override-test-app1.yaml")
    )
      .map { File(it.file) }

    val config = MiskConfig.load<TestConfig>("test_app", defaultEnv, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 79))
  }

  @Test
  fun handlesNonExistentExternalFile() {
    // A common config does not exist, but the testing config does, loading the config should not fail
    val config = MiskConfig.load<DurationConfig>("no_common_config_app", defaultEnv, listOf())
    assertThat(config.interval).isEqualTo(Duration.ofSeconds(23))
  }
}

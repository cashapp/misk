package misk.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.util.Modules
import misk.environment.DeploymentModule
import misk.logging.LogCollectorModule
import misk.logging.LogCollectorService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import wisp.deployment.TESTING
import wisp.logging.LogCollector
import java.io.File
import java.time.Duration
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
class MiskConfigTest {
  val config = MiskConfig.load<TestConfig>("test_app", TESTING)

  @MiskTestModule
  val module = Modules.combine(
    ConfigModule.create("test_app", config),
    DeploymentModule(TESTING),
    LogCollectorModule()
    // @TODO(jwilson) https://github.com/square/misk/issues/272
  )

  @Inject
  private lateinit var testConfig: TestConfig

  @Inject
  private lateinit var logCollector: LogCollector

  @Inject
  private lateinit var logCollectorService: LogCollectorService

  @BeforeEach
  fun setUp() {
    logCollectorService.startAsync()
  }

  @AfterEach
  fun tearDown() {
    logCollectorService.stopAsync()
  }

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
      MiskConfig.load<TestConfig>("missing", TESTING)
    }

    assertThat(exception).hasMessageContaining(
      "could not find configuration files -" +
        " checked [classpath:/missing-common.yaml, classpath:/missing-testing.yaml]"
    )
  }

  @Test
  fun friendlyErrorMessageWhenConfigPropertyMissing() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("partial_test_app", TESTING)
    }

    assertThat(exception).hasMessageContaining(
      "could not find 'consumer_a' of 'TestConfig' in partial_test_app-testing.yaml"
    )
  }

  @Test
  fun friendlyErrorMessagesWhenFileUnparseable() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("unparsable", TESTING)
    }

    assertThat(exception).hasMessageContaining("could not parse classpath:/unparsable-common.yaml")
  }

  @Test
  fun friendlyLogsWhenPropertiesNotFound() {
    // Unknown properties don't cause an exception. If a missing property were misspelled, it
    // would fail because of it being a missing property, rather than due to a new property being
    // present.
    MiskConfig.load<TestConfig>("unknownproperty", TESTING)

    assertThat(logCollector.takeMessages(MiskConfig::class))
      .hasSize(1)
      .allMatch { it.contains("'consumer_b.blue_items' not found") }
  }

  @Test
  fun misspelledRequiredPrimitivePropertiesFail() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("misspelledproperty", TESTING)
    }

    assertThat(exception).hasMessageContaining(
      "could not find 'consumer_b.max_items' of 'TestConfig' in misspelledproperty-testing.yaml"
    )
    // Also contains a message about similar properties that it had found.
    assertThat(exception).hasMessageContaining("consumer_b.mox_items")
  }

  @Test
  fun misspelledRequiredObjectPropertiesFail() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("misspelledobject", TESTING)
    }

    assertThat(exception).hasMessageContaining(
      "could not find 'duration.interval' of 'TestConfig' in misspelledobject-testing.yaml"
    )
    // Also contains a message about similar properties that it had found.
    assertThat(exception).hasMessageContaining("duration.intervel")
  }

  @Test
  fun misspelledRequiredStringPropertiesFail() {
    val exception = assertFailsWith<IllegalStateException> {
      MiskConfig.load<TestConfig>("misspelledstring", TESTING)
    }

    assertThat(exception).hasMessageContaining(
      "could not find 'nested.child_nested.nested_value' of 'TestConfig' in misspelledstring-testing.yaml"
    )
    // Also contains a message about similar properties that it had found.
    assertThat(exception).hasMessageContaining("nested.child_nested.nexted_value")
  }

  @Test
  fun mergesExternalFiles() {
    val overrides = listOf(
      MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml"),
      MiskConfigTest::class.java.getResource("/overrides/override-test-app2.yaml")
    )
      .map { File(it.file) }

    val config = MiskConfig.load<TestConfig>("test_app", TESTING, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 122))
  }

  @Test
  fun mergesResources() {
    val overrides = listOf(
      "classpath:/overrides/override-test-app1.yaml",
      "classpath:/overrides/override-test-app2.yaml"
    )
    val config = MiskConfig.load<TestConfig>("test_app", TESTING, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 122))
  }

  @Test
  fun mergesResourcesAndFiles() {
    val file = MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml")!!.file
    val overrides = listOf(
      "filesystem:${file}",
      "classpath:/overrides/override-test-app2.yaml"
    )
    val config = MiskConfig.load<TestConfig>("test_app", TESTING, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 122))
  }

  @Test
  fun mergesJsonNode() {
    val overrideString = """{ "consumer_a": { "min_items": "12", "max_items": 86 }}"""
    val overrideValue = ObjectMapper().readTree(overrideString)
    val config = MiskConfig.load<TestConfig>("test_app", TESTING, listOf(), overrideValue)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(12, 86))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(1, 2))
  }

  @Test
  fun mergesResourcesAndJsonNode() {
    val overrideString = """{ "consumer_a": { "min_items": "12", "max_items": 86 }}"""
    val overrideValue = ObjectMapper().readTree(overrideString)

    val file = MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml")!!.file
    val overrideResources = listOf(
      "filesystem:${file}",
      "classpath:/overrides/override-test-app2.yaml"
    )
    val config = MiskConfig.load<TestConfig>("test_app", TESTING, overrideResources, overrideValue)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(12, 86))
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

    val config = MiskConfig.load<TestConfig>("test_app", TESTING, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 79))
  }

  @Test
  fun handlesNonExistentExternalFile() {
    // A common config does not exist, but the testing config does, loading the config should not fail
    val config = MiskConfig.load<DurationConfig>("no_common_config_app", TESTING, listOf<File>())
    assertThat(config.interval).isEqualTo(Duration.ofSeconds(23))
  }
}

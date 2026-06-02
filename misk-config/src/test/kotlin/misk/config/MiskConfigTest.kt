package misk.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.util.Modules
import jakarta.inject.Inject
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import misk.environment.DeploymentModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.logging.LogCollectorService
import misk.resources.FakeFilesModule
import misk.resources.ResourceLoader
import misk.resources.TestingResourceLoaderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.event.Level
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import wisp.deployment.TESTING

@ExtendWith(SystemStubsExtension::class)
@MiskTest
class MiskConfigTest {
  val config = MiskConfig.load<TestConfig>("test_app", TESTING)

  @Inject private lateinit var resourceLoader: ResourceLoader

  @MiskTestModule
  val module =
    Modules.combine(
      ConfigModule.create("test_app", config),
      DeploymentModule(TESTING),
      LogCollectorModule(),
      // @TODO(jwilson) https://github.cgcobom/square/misk/issues/272
      TestingResourceLoaderModule(),
      FakeFilesModule(
        mapOf(
          "/misk/resources/secrets/secret_information_values.yaml" to
            """
            |answer_to_universe: 42
            |limit: 5
            """
              .trimMargin()
        )
      ),
    )

  @Inject private lateinit var testConfig: TestConfig
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var logCollectorService: LogCollectorService

  @SystemStub
  private val envVars =
    EnvironmentVariables(
      "STRING_VALUE",
      "abc123",
      "NUMBER_VALUE",
      "14",
      "BOOLEAN_VALUE",
      "true",
      "SECRET_API_KEY",
      "def456",
      "SECRET_NUMBER",
      "42",
    )

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
    assertThat((testConfig.action_exception_log_level))
      .isEqualTo(ActionExceptionLogLevelConfig(Level.INFO, Level.ERROR))
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
    val exception = assertFailsWith<IllegalStateException> { MiskConfig.load<TestConfig>("missing", TESTING) }

    assertThat(exception)
      .hasMessageContaining(
        "could not find configuration files -" +
          " checked [classpath:/missing-common.yaml, classpath:/missing-testing.yaml]"
      )
  }

  @Test
  fun friendlyErrorMessageWhenConfigPropertyMissing() {
    val exception = assertFailsWith<IllegalStateException> { MiskConfig.load<TestConfig>("partial_test_app", TESTING) }

    assertThat(exception)
      .hasMessageContaining("could not find 'consumer_a' of 'TestConfig' in partial_test_app-testing.yaml")
  }

  @Test
  fun friendErrorMessageWhenConfigPropertyMissingInList() {
    val exception =
      assertFailsWith<IllegalStateException> { MiskConfig.load<TestConfig>("missing_property_in_list", TESTING) }

    assertThat(exception)
      .hasMessageContaining(
        "could not find 'collection.0.name' of 'TestConfig' in missing_property_in_list-testing.yaml"
      )
  }

  @Test
  fun friendlyErrorMessagesWhenFileUnparseable() {
    val exception = assertFailsWith<IllegalStateException> { MiskConfig.load<TestConfig>("unparsable", TESTING) }

    assertThat(exception).hasMessageContaining("could not parse classpath:/unparsable-common.yaml")
  }

  @Test
  fun configLoadsValuesFromEnvironmentVariables() {
    // Set environment variables before loading config
    assertEquals("abc123", System.getenv("STRING_VALUE"))
    assertEquals("14", System.getenv("NUMBER_VALUE"))
    assertEquals("true", System.getenv("BOOLEAN_VALUE"))
    assertEquals("def456", System.getenv("SECRET_API_KEY"))
    assertEquals("42", System.getenv("SECRET_NUMBER"))

    val actual = MiskConfig.load<EnvironmentTestConfig>("environment", TESTING)
    assertEquals("abc123", actual.string_value)
    assertEquals("classpath:/path/to/not/load", actual.ignored_classpath_value)
    assertEquals(14, actual.int_value)
    assertEquals(14, actual.long_value)
    assertEquals(14f, actual.float_value)
    assertEquals(true, actual.boolean_value)
    assertEquals("default", actual.default_string)
    assertEquals(32, actual.default_int)
    assertEquals(true, actual.default_boolean)
    assertEquals("def456", actual.secret_api_key.value)
    assertEquals(42, actual.secret_int.value)
    assertEquals(42, actual.secret_long.value)
    assertEquals(42f, actual.secret_float.value)

    // URL defaults with colons - these test the fix for the original issue
    // Since the environment variables don't exist, these should use the default values
    assertEquals("http://localhost:8888", actual.http_url_default)
    assertEquals("jdbc:postgresql://localhost:5432/database", actual.jdbc_url_default)
    assertEquals("https://example.com:443/api/v1", actual.https_url_with_port_default)

    // Non-secret values should not be redacted
    assertThat(actual.toString()).contains("abc123")
    assertThat(actual.toString()).contains("14")
    assertThat(actual.toString()).contains("http://localhost:8888")
    assertThat(actual.toString()).contains("jdbc:postgresql://localhost:5432/database")

    // Secret value should be redacted
    assertThat(actual.toString()).doesNotContain("def456")
    assertThat(actual.toString()).doesNotContain("42")
  }

  @Test
  fun poorlyFormedEnvironmentVariableThrows() {
    val exception =
      assertFailsWith<IllegalStateException> { MiskConfig.load<EnvironmentTestConfig>("environment_throws", TESTING) }
    assertThat(exception)
      .hasMessageContaining(
        "Resource references for non-Secret fields must be in the form of \${scheme:path} or \${scheme:path:-defaultValue}"
      )
  }

  @Test
  fun environmentVariablesWithUrlDefaultsContainingColons() {
    // This test specifically verifies the fix for the original issue where URLs with colons
    // in default values would cause IllegalStateException
    val actual = MiskConfig.load<EnvironmentTestConfig>("environment", TESTING)

    // These environment variables don't exist, so should use the default values
    // The key test is that these don't throw exceptions during parsing
    assertThat(actual.http_url_default).isEqualTo("http://localhost:8888")
    assertThat(actual.jdbc_url_default).isEqualTo("jdbc:postgresql://localhost:5432/database")
    assertThat(actual.https_url_with_port_default).isEqualTo("https://example.com:443/api/v1")

    // Verify the URLs are properly included in toString (not redacted)
    assertThat(actual.toString()).contains("http://localhost:8888")
    assertThat(actual.toString()).contains("jdbc:postgresql://localhost:5432/database")
    assertThat(actual.toString()).contains("https://example.com:443/api/v1")
  }

  @Test
  fun friendlyLogsWhenPropertiesNotFound() {
    // By default, unknown properties don't cause an exception. If a missing property were misspelled, it
    // would fail because of it being a missing property, rather than due to a new property being
    // present.
    MiskConfig.load<TestConfig>("unknownproperty", TESTING)

    assertThat(logCollector.takeMessages(MiskConfig::class)).hasSize(1).allMatch {
      it.contains("'consumer_b.blue_items' not found")
    }
  }

  @Test
  fun failsWhenPropertiesNotFoundAndFailOnUnknownPropertiesIsEnabled() {
    val exception =
      assertFailsWith<IllegalStateException> {
        MiskConfig.load<TestConfig>(TestConfig::class.java, "unknownproperty", TESTING, failOnUnknownProperties = true)
      }
    assertThat(exception).hasMessageContaining("Unrecognized field \"blue_items\"")
  }

  @Test
  fun friendLogsWhenConfigPropertyNotFoundInList() {
    MiskConfig.load<TestConfig>("unknownproperty_in_list", TESTING)

    assertThat(logCollector.takeMessages(MiskConfig::class)).hasSize(1).allMatch {
      it.contains("'collection.0.power_level' not found")
    }
  }

  @Test
  fun misspelledRequiredPrimitivePropertiesFail() {
    val exception =
      assertFailsWith<IllegalStateException> { MiskConfig.load<TestConfig>("misspelledproperty", TESTING) }

    assertThat(exception)
      .hasMessageContaining("could not find 'consumer_b.max_items' of 'TestConfig' in misspelledproperty-testing.yaml")
    // Also contains a message about similar properties that it had found.
    assertThat(exception).hasMessageContaining("consumer_b.mox_items")
  }

  @Test
  fun misspelledRequiredObjectPropertiesFail() {
    val exception = assertFailsWith<IllegalStateException> { MiskConfig.load<TestConfig>("misspelledobject", TESTING) }

    assertThat(exception)
      .hasMessageContaining("could not find 'duration.interval' of 'TestConfig' in misspelledobject-testing.yaml")
    // Also contains a message about similar properties that it had found.
    assertThat(exception).hasMessageContaining("duration.intervel")
  }

  @Test
  fun misspelledRequiredStringPropertiesFail() {
    val exception = assertFailsWith<IllegalStateException> { MiskConfig.load<TestConfig>("misspelledstring", TESTING) }

    assertThat(exception)
      .hasMessageContaining(
        "could not find 'nested.child_nested.nested_value' of 'TestConfig' in misspelledstring-testing.yaml"
      )
    // Also contains a message about similar properties that it had found.
    assertThat(exception).hasMessageContaining("nested.child_nested.nexted_value")
  }

  @Test
  fun mergesExternalFiles() {
    val overrides =
      listOf(
          MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml"),
          MiskConfigTest::class.java.getResource("/overrides/override-test-app2.yaml"),
        )
        .map { File(it.file) }

    val config = MiskConfig.load<TestConfig>("test_app", TESTING, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 122))
  }

  @Test
  fun mergesResources() {
    val overrides =
      listOf("classpath:/overrides/override-test-app1.yaml", "classpath:/overrides/override-test-app2.yaml")
    val config = MiskConfig.load<TestConfig>("test_app", TESTING, overrides)
    assertThat(config.consumer_a).isEqualTo(ConsumerConfig(14, 27))
    assertThat(config.consumer_b).isEqualTo(ConsumerConfig(34, 122))
  }

  @Test
  fun mergesResourcesAndFiles() {
    val file = MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml")!!.file
    val overrides = listOf("filesystem:${file}", "classpath:/overrides/override-test-app2.yaml")
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
    val overrideResources = listOf("filesystem:${file}", "classpath:/overrides/override-test-app2.yaml")
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
    val overrides =
      listOf(
          MiskConfigTest::class.java.getResource("/overrides/override-test-app1.yaml"),
          MiskConfigTest::class.java.getResource("/additional_overrides/override-test-app1.yaml"),
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

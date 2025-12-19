package misk.logging

import ch.qos.logback.classic.LoggerContext
import com.google.inject.Provides
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.feature.Feature
import misk.feature.testing.FakeFeatureFlags
import misk.feature.testing.FakeFeatureFlagsModule
import misk.inject.KAbstractModule
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Integration test for DynamicLogLevelModule that verifies the complete flow: Feature Flag -> Service -> Controller ->
 * Filter
 */
@MiskTest(startService = true)
class DynamicLogLevelIntegrationTest {

  @MiskTestModule val module = TestModule()

  @Inject lateinit var service: DynamicLogLevelService
  @Inject lateinit var featureFlags: FakeFeatureFlags

  @BeforeEach
  fun setup() {
    // Clear any existing filters from the Logback context to ensure test isolation
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    context.turboFilterList.removeIf { it.javaClass.simpleName == "LogLevelFilter" }

    // Clear the feature flag to start each test with a clean state
    featureFlags.override(Feature("test-dynamic-logging"), "")
  }

  @Test
  fun `service processes feature flag and applies turbo filter`() {
    // Configure feature flag with multiple loggers
    featureFlags.override(
      Feature("test-dynamic-logging"),
      "com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient=TRACE,com.squareup.cash.dynamodb.lease=DEBUG",
    )

    // Trigger update
    service.updateLoggingLevels()

    // Verify turbo filter was added to logback context
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val turboFilters = context.turboFilterList

    assertThat(turboFilters).isNotEmpty
    assertThat(turboFilters.any { it.javaClass.simpleName == "LogLevelFilter" }).isTrue()

    // Verify dynamicLevels contents
    val dynamicLevels = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels).hasSize(2)
    assertThat(dynamicLevels["com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient"])
      .isEqualTo(ch.qos.logback.classic.Level.TRACE)
    assertThat(dynamicLevels["com.squareup.cash.dynamodb.lease"]).isEqualTo(ch.qos.logback.classic.Level.DEBUG)
  }

  @Test
  fun `service removes filter when feature flag is cleared`() {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val initialCount = context.turboFilterList.count { it.javaClass.simpleName == "LogLevelFilter" }

    // Set a flag
    featureFlags.override(Feature("test-dynamic-logging"), "com.test=DEBUG")
    service.updateLoggingLevels()

    val withFilterCount = context.turboFilterList.count { it.javaClass.simpleName == "LogLevelFilter" }
    assertThat(withFilterCount).isGreaterThan(initialCount)

    // Verify dynamicLevels contents before clearing
    val dynamicLevels = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels).hasSize(1)
    assertThat(dynamicLevels["com.test"]).isEqualTo(ch.qos.logback.classic.Level.DEBUG)

    // Then clear it
    featureFlags.override(Feature("test-dynamic-logging"), "")
    service.updateLoggingLevels()

    // Verify filter was removed (back to initial count)
    val finalCount = context.turboFilterList.count { it.javaClass.simpleName == "LogLevelFilter" }
    assertThat(finalCount).isEqualTo(initialCount)
  }

  @Test
  fun `service handles malformed feature flag gracefully`() {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val initialCount = context.turboFilterList.count { it.javaClass.simpleName == "LogLevelFilter" }

    // Configure invalid feature flag
    featureFlags.override(Feature("test-dynamic-logging"), "invalid format without equals")

    // Should not throw exception
    service.updateLoggingLevels()

    // Verify no NEW filter was added (count should stay the same)
    val finalCount = context.turboFilterList.count { it.javaClass.simpleName == "LogLevelFilter" }
    assertThat(finalCount).isEqualTo(initialCount)
  }

  @Test
  fun `service replaces filter when feature flag changes`() {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext

    // Set initial flag
    featureFlags.override(Feature("test-dynamic-logging"), "com.test1=DEBUG")
    service.updateLoggingLevels()

    val initialFilterCount = context.turboFilterList.count { it.javaClass.simpleName == "LogLevelFilter" }
    assertThat(initialFilterCount).isGreaterThan(0)

    // Verify first dynamicLevels contents
    val dynamicLevels1 = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels1).hasSize(1)
    assertThat(dynamicLevels1["com.test1"]).isEqualTo(ch.qos.logback.classic.Level.DEBUG)

    // Update flag to different value
    featureFlags.override(Feature("test-dynamic-logging"), "com.test2=TRACE")
    service.updateLoggingLevels()

    // Should still have same number of filters (old one replaced, not added)
    val updatedFilterCount = context.turboFilterList.count { it.javaClass.simpleName == "LogLevelFilter" }
    assertThat(updatedFilterCount).isEqualTo(initialFilterCount)

    // Verify second dynamicLevels contents
    val dynamicLevels2 = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels2).hasSize(1)
    assertThat(dynamicLevels2["com.test2"]).isEqualTo(ch.qos.logback.classic.Level.TRACE)
    assertThat(dynamicLevels2["com.test1"]).isNull() // Old entry should be gone
  }

  @Test
  fun `service supports prefix matching for package hierarchies`() {
    // Configure with package prefix
    featureFlags.override(Feature("test-dynamic-logging"), "com.squareup.cash.dynamodb=TRACE,com.amazonaws=DEBUG")

    service.updateLoggingLevels()

    // Verify filter was added
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    assertThat(context.turboFilterList.any { it.javaClass.simpleName == "LogLevelFilter" }).isTrue()

    // Verify dynamicLevels contents
    val dynamicLevels = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels).hasSize(2)
    assertThat(dynamicLevels["com.squareup.cash.dynamodb"]).isEqualTo(ch.qos.logback.classic.Level.TRACE)
    assertThat(dynamicLevels["com.amazonaws"]).isEqualTo(ch.qos.logback.classic.Level.DEBUG)
  }

  @Test
  fun `service handles multiple logger configurations`() {
    // Configure multiple specific loggers
    featureFlags.override(
      Feature("test-dynamic-logging"),
      "com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient=TRACE," +
        "com.squareup.cash.dynamodb.lease.RealDynamoDbLease=DEBUG," +
        "com.squareup.cash.roundtable=TRACE",
    )

    service.updateLoggingLevels()

    // Verify filter was added
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val turboFilters = context.turboFilterList

    assertThat(turboFilters).isNotEmpty
    assertThat(turboFilters.any { it.javaClass.simpleName == "LogLevelFilter" }).isTrue()

    // Verify dynamicLevels contents
    val dynamicLevels = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels).hasSize(3)
    assertThat(dynamicLevels["com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient"])
      .isEqualTo(ch.qos.logback.classic.Level.TRACE)
    assertThat(dynamicLevels["com.squareup.cash.dynamodb.lease.RealDynamoDbLease"])
      .isEqualTo(ch.qos.logback.classic.Level.DEBUG)
    assertThat(dynamicLevels["com.squareup.cash.roundtable"]).isEqualTo(ch.qos.logback.classic.Level.TRACE)
  }

  @Test
  fun `service correctly parses and stores dynamic levels in filter`() {
    // Configure with multiple loggers and different levels
    featureFlags.override(
      Feature("test-dynamic-logging"),
      "com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient=TRACE," +
        "com.squareup.cash.dynamodb.lease=DEBUG," +
        "com.squareup.cash.roundtable=TRACE",
    )

    service.updateLoggingLevels()

    // Get the filter
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val filter = context.turboFilterList.find { it.javaClass.simpleName == "LogLevelFilter" }

    assertThat(filter).isNotNull

    val dynamicLevels = getDynamicLevelsFromFilter(context)

    // Verify the map contains exactly what we configured
    assertThat(dynamicLevels).hasSize(3)
    assertThat(dynamicLevels["com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient"])
      .isEqualTo(ch.qos.logback.classic.Level.TRACE)
    assertThat(dynamicLevels["com.squareup.cash.dynamodb.lease"]).isEqualTo(ch.qos.logback.classic.Level.DEBUG)
    assertThat(dynamicLevels["com.squareup.cash.roundtable"]).isEqualTo(ch.qos.logback.classic.Level.TRACE)
  }

  @Test
  fun `service skips update when feature flag value unchanged`() {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext

    // Set initial flag
    featureFlags.override(Feature("test-dynamic-logging"), "com.test=DEBUG")
    service.updateLoggingLevels()

    val filter1 = context.turboFilterList.find { it.javaClass.simpleName == "LogLevelFilter" }

    // Verify dynamicLevels contents
    val dynamicLevels1 = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels1).hasSize(1)
    assertThat(dynamicLevels1["com.test"]).isEqualTo(ch.qos.logback.classic.Level.DEBUG)

    // Set same flag again
    featureFlags.override(Feature("test-dynamic-logging"), "com.test=DEBUG")
    service.updateLoggingLevels()

    val filter2 = context.turboFilterList.find { it.javaClass.simpleName == "LogLevelFilter" }

    // Should be the same filter instance (not replaced)
    assertThat(filter1).isSameAs(filter2)

    // Verify dynamicLevels contents remain the same
    val dynamicLevels2 = getDynamicLevelsFromFilter(context)
    assertThat(dynamicLevels2).hasSize(1)
    assertThat(dynamicLevels2["com.test"]).isEqualTo(ch.qos.logback.classic.Level.DEBUG)
  }

  /** Helper method to extract dynamicLevels from the LogLevelFilter using reflection. */
  private fun getDynamicLevelsFromFilter(context: LoggerContext): Map<String, ch.qos.logback.classic.Level> {
    val filter = context.turboFilterList.find { it.javaClass.simpleName == "LogLevelFilter" } ?: return emptyMap()

    val dynamicLevelsField = filter.javaClass.getDeclaredField("dynamicLevels")
    dynamicLevelsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return dynamicLevelsField.get(filter) as Map<String, ch.qos.logback.classic.Level>
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(misk.MiskTestingServiceModule(installFakeMetrics = true))
      install(FakeFeatureFlagsModule())
      // Override the config directly since we're in a test
      bind<DynamicLoggingConfig>()
        .toInstance(DynamicLoggingConfig(enabled = true, feature_flag_name = "test-dynamic-logging"))
      install(ServiceModule<RepeatedTaskQueue>(DynamicLogLevel::class))
      install(
        ServiceModule<DynamicLogLevelService>()
          .dependsOn<RepeatedTaskQueue>(DynamicLogLevel::class)
          .dependsOn<ReadyService>()
      )
    }

    @Provides @DynamicLogLevel fun providesDynamicLogLevel(): Feature = Feature("test-dynamic-logging")

    @Provides
    @Singleton
    @DynamicLogLevel
    fun repeatedTaskQueue(queueFactory: RepeatedTaskQueueFactory) = queueFactory.new("dynamic-log-level-queue")
  }
}

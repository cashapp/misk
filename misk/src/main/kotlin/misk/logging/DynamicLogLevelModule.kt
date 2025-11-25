package misk.logging

import com.google.inject.BindingAnnotation
import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory

/**
 * Module that enables runtime log level control via feature flags for debugging issues.
 *
 * This allows you to temporarily increase log verbosity for specific loggers without restarting the service, useful for
 * troubleshooting issues in staging/production environments.
 *
 * ## Configuration
 *
 * ### 1. Install the module
 *
 * ```kotlin
 * install(DynamicLogLevelModule(DynamicLoggingConfig(enabled=true, feature_flag_name="dynamic-log-level")))
 * ```
 *
 * ### 2. Set the feature flag
 * Set the feature flag value to a comma-separated string of `logger=LEVEL` pairs:
 * ```
 * com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient=TRACE,com.squareup.cash.dynamodb.lease=DEBUG
 * ```
 *
 * **Supported levels:** `TRACE`, `DEBUG` (other levels are ignored for safety)
 *
 * **To disable:** Set the feature flag to an empty string `""` to remove all dynamic log level overrides
 *
 * **Logger names can be:**
 * - Fully qualified class name: `com.example.MyClass`
 * - Package prefix: `com.example` (applies to all classes in that package)
 *
 * The service polls the feature flag every 20 seconds and applies changes automatically.
 *
 * ## Important: Logging Framework Bridges
 *
 * This module controls Logback log levels. If the logger you want to control uses a different logging framework, you
 * **must** add the appropriate SLF4J bridge to route logs through Logback:
 * ```kotlin
 * // In your build.gradle.kts
 * implementation(libs.slf4jJclBridge) // Bridge Commons Logging (e.g., AmazonDynamoDBLockClient)
 * implementation(libs.slf4jJulBridge) // Bridge Java Util Logging
 * ```
 *
 * Without the bridges, log level changes won't affect loggers using those frameworks.
 */
class DynamicLogLevelModule(val config: DynamicLoggingConfig) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<RepeatedTaskQueue>(DynamicLogLevel::class))
    install(ServiceModule<DynamicLogLevelService>()
      .dependsOn<RepeatedTaskQueue>(DynamicLogLevel::class)
      .dependsOn<ReadyService>()
    )
    bind<DynamicLoggingConfig>().toInstance(config)
  }

  @Provides
  @Singleton
  @DynamicLogLevel
  fun repeatedTaskQueue(queueFactory: RepeatedTaskQueueFactory) = queueFactory.new("dynamic-log-level-queue")
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@BindingAnnotation
annotation class DynamicLogLevel
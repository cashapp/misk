package misk.jooq.config

import com.google.common.annotations.VisibleForTesting
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jooq.JooqTransacter
import misk.jooq.TransactionIsolationLevel
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import org.jooq.Configuration

/**
 * This [ConfigurationFactory] generates configurations and caches them by their associated [TransactionIsolationLevel]
 * and read-only flag.
 *
 * This is doable because these are the only user-modifiable parameters that can change the [Configuration] after the
 * Guice injector is built (i.e. after JooqModule is installed). It is important to cache the [Configuration] and share
 * it among transactions because [it contains the reflection cache](
 * https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/thread-safety/).
 */
internal class CachedConfigurationFactory(
  private val clock: Clock,
  private val dataSourceConfig: DataSourceConfig,
  private val dataSourceService: DataSourceService,
  private val jooqCodeGenSchemaName: String,
  private val jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions,
  private val jooqConfigExtension: Configuration.() -> Unit = {},
) : ConfigurationFactory() {
  private data class CacheKey(val isolationLevel: TransactionIsolationLevel, val readOnly: Boolean)

  private fun JooqTransacter.TransacterOptions.toCacheKey() = CacheKey(isolationLevel, readOnly)

  private val concurrentHashMap = ConcurrentHashMap<CacheKey, Configuration>()

  @VisibleForTesting
  val cacheSize: Int
    get() = concurrentHashMap.size

  override fun getConfiguration(options: JooqTransacter.TransacterOptions): Configuration {
    return concurrentHashMap.computeIfAbsent(options.toCacheKey()) {
      buildConfiguration(
          clock = clock,
          dataSourceConfig = dataSourceConfig,
          dataSourceService = dataSourceService,
          jooqCodeGenSchemaName = jooqCodeGenSchemaName,
          jooqTimestampRecordListenerOptions = jooqTimestampRecordListenerOptions,
          options = options,
        )
        .apply(jooqConfigExtension)
    }
  }
}

package misk.jooq.config

import com.google.common.annotations.VisibleForTesting
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jooq.JooqTransacter
import misk.jooq.TransactionIsolationLevel
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import org.jooq.Configuration
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * This [ConfigurationFactory] generates configurations and caches them by their associated [TransactionIsolationLevel].
 *
 * This is doable because the only user-modifiable parameter that can change the [Configuration] after the Guice
 * injector is built (i.e. after JooqModule is installed), is the transaction isolation level. It is important to
 * cache the [Configuration] and share it among transactions because [it contains the reflection cache](
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
  private val concurrentHashMap = ConcurrentHashMap<TransactionIsolationLevel, Configuration>()

  @VisibleForTesting
  val cacheContents
    get() = concurrentHashMap.entries.toSet()

  override fun getConfiguration(options: JooqTransacter.TransacterOptions): Configuration {
    return concurrentHashMap.getOrPut(options.isolationLevel) {
      buildConfiguration(
        clock = clock,
        dataSourceConfig = dataSourceConfig,
        dataSourceService = dataSourceService,
        jooqCodeGenSchemaName = jooqCodeGenSchemaName,
        jooqTimestampRecordListenerOptions = jooqTimestampRecordListenerOptions,
        options = options,
      ).apply(jooqConfigExtension)
    }
  }
}

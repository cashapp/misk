package misk.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import misk.inject.KAbstractModule
import misk.inject.toKey
import javax.inject.Provider
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Binds a [DataSourceCluster] configured from the specific entry in the
 * [DataSourceClustersConfig] and binds a [DataSource] for the [writer].
 *
 * N.B. If a [reader] config is not provided the [writer] config will be used for the [reader].
 */
class DataSourceModule constructor(
  private val config: DataSourceClusterConfig,
  private val qualifier: KClass<out Annotation>
) : KAbstractModule() {

  override fun configure() {
    val clusterKey = DataSourceCluster::class.toKey(qualifier)
    val clusterProvider = getProvider(clusterKey)

    bind(clusterKey)
        .toProvider(DataSourceClusterProvider(config))
        .asEagerSingleton()

    bind(DataSource::class.toKey(qualifier))
        .toProvider(Provider<DataSource> { clusterProvider.get().writer })
        .asEagerSingleton()
  }

  private class DataSourceClusterProvider(
    private val config: DataSourceClusterConfig
  ) : Provider<DataSourceCluster> {
    override fun get(): DataSourceCluster {
      val writer = HikariDataSource(toHikariConfig(config.writer, readOnly = false))
      val reader = HikariDataSource(toHikariConfig(config.reader ?: config.writer, readOnly = true))
      return DataSourceCluster(writer, reader)
    }

    private fun toHikariConfig(config: DataSourceConfig, readOnly: Boolean): HikariConfig {
      val hikariConfig = HikariConfig()
      hikariConfig.driverClassName = config.type.driverClassName
      hikariConfig.jdbcUrl = config.type.buildJdbcUrl(config)
      hikariConfig.isReadOnly = readOnly
      config.username?.let { hikariConfig.username = it }
      config.password?.let { hikariConfig.password = it }
      config.connection_properties.forEach { name, value ->
        hikariConfig.dataSourceProperties[name] = value
      }
      hikariConfig.maximumPoolSize = config.fixed_pool_size
      hikariConfig.connectionTimeout = config.connection_timeout.toMillis()
      hikariConfig.maxLifetime = config.connection_max_lifetime.toMillis()

      return hikariConfig
    }
  }
}

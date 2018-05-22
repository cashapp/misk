package misk.jdbc

import com.google.inject.Key
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import misk.inject.KAbstractModule
import javax.inject.Inject
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
  private val datasourceName: String,
  private val clusterKey: Key<DataSourceCluster>,
  private val dataSourceKey: Key<DataSource>
) : KAbstractModule() {

  override fun configure() {
    bind(clusterKey)
      .toProvider(DataSourceClusterProvider(datasourceName))
      .asEagerSingleton()
    val clusterProvider = getProvider(clusterKey)

    bind(dataSourceKey)
      .toProvider(Provider<DataSource> { clusterProvider.get().writer })
      .asEagerSingleton()
  }

  companion object {
    fun create(datasourceName: String) =
      DataSourceModule(
        datasourceName,
        Key.get(DataSourceCluster::class.java),
        Key.get(DataSource::class.java)
      )

    fun create(datasourceName: String, annotatedBy: Annotation) =
      DataSourceModule(
        datasourceName,
        Key.get(DataSourceCluster::class.java, annotatedBy),
        Key.get(DataSource::class.java, annotatedBy)
      )

    fun <A : Annotation> create(datasourceName: String, annotatedBy: Class<A>) =
      DataSourceModule(
        datasourceName,
        Key.get(DataSourceCluster::class.java, annotatedBy),
        Key.get(DataSource::class.java, annotatedBy)
      )

    fun <A : Annotation> create(datasourceName: String, annotatedBy: KClass<A>) =
      DataSourceModule(
        datasourceName,
        Key.get(DataSourceCluster::class.java, annotatedBy.java),
        Key.get(DataSource::class.java, annotatedBy.java)
      )
  }

  private class DataSourceClusterProvider(
    private val datasourceName: String
  ) : Provider<DataSourceCluster> {
    @Inject lateinit var dataSourceClustersConfig: DataSourceClustersConfig

    override fun get(): DataSourceCluster {
      val config = dataSourceClustersConfig[datasourceName]
          ?: throw IllegalStateException("no datasource cluster named $datasourceName")

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

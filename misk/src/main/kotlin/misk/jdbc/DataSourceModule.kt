package misk.jdbc

import com.google.inject.Key
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import javax.inject.Inject
import javax.inject.Provider
import javax.sql.DataSource
import kotlin.reflect.KClass

/** Binds a [DataSource] configured from the specific entry in the [DataSourcesConfig] */
class DataSourceModule constructor(
  private val datasourceName: String,
  private val key: Key<DataSource>
) : KAbstractModule() {

  override fun configure() {
    bind(key).toProvider(DataSourceProvider(datasourceName)).asEagerSingleton()
  }

  companion object {
    fun create(datasourceName: String) =
        DataSourceModule(datasourceName, Key.get(DataSource::class.java))

    fun create(datasourceName: String, annotatedBy: Annotation) =
        DataSourceModule(datasourceName, Key.get(DataSource::class.java, annotatedBy))

    fun <A : Annotation> create(datasourceName: String, annotatedBy: Class<A>) =
        DataSourceModule(datasourceName, Key.get(DataSource::class.java, annotatedBy))

    fun <A : Annotation> create(datasourceName: String, annotatedBy: KClass<A>) =
        DataSourceModule(datasourceName, Key.get(DataSource::class.java, annotatedBy.java))
  }

  private class DataSourceProvider(private val datasourceName: String) : Provider<DataSource> {
    @Inject lateinit var datasourcesConfig: DataSourcesConfig

    override fun get(): DataSource {
      val config = datasourcesConfig.databases[datasourceName]
          ?: throw IllegalStateException("no datasource named $datasourceName")

      val hikariConfig = HikariConfig()
      hikariConfig.driverClassName = config.type.driverClassName
      hikariConfig.jdbcUrl = config.type.buildJdbcUrl(config)
      hikariConfig.isReadOnly = config.read_only
      config.username?.let { hikariConfig.username = it }
      config.password?.let { hikariConfig.password = it }
      config.connection_properties.forEach { name, value ->
        hikariConfig.dataSourceProperties[name] = value
      }
      hikariConfig.maximumPoolSize = config.fixed_pool_size
      hikariConfig.connectionTimeout = config.connection_timeout.toMillis()
      hikariConfig.maxLifetime = config.connection_max_lifetime.toMillis()

      return HikariDataSource(hikariConfig)
    }
  }
}
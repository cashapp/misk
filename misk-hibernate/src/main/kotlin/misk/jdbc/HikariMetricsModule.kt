package misk.jdbc

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import misk.inject.KAbstractModule
import misk.metrics.Metrics
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import javax.sql.DataSource
import kotlin.reflect.KClass

class HikariMetricsModule(private val qualifier: KClass<out Annotation>) : KAbstractModule() {
  override fun configure() {
    multibind<DataSourceDecorator>(qualifier).toProvider(object : Provider<DataSourceDecorator> {
      @Inject private lateinit var metrics: Metrics

      override fun get(): DataSourceDecorator = HikariMetricsDecorator(metrics)
    })
  }
}

@Singleton
private class HikariMetricsDecorator(private val metrics: Metrics) : DataSourceDecorator {
  override fun decorate(dataSource: DataSource): DataSource {
    val concreteDataSource = when (dataSource) {
      is DecoratedDataSource -> dataSource.resolve()
      else -> dataSource
    }

    if (concreteDataSource is HikariDataSource) {
      concreteDataSource.metricsTrackerFactory = PrometheusMetricsTrackerFactory(metrics.registry)
    }

    return dataSource
  }
}

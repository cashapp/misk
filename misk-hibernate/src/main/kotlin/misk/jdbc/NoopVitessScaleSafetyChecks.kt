package misk.jdbc

import com.squareup.moshi.Moshi
import misk.vitess.StartVitessService
import net.ttddyy.dsproxy.proxy.ProxyConfig
import net.ttddyy.dsproxy.support.ProxyDataSource
import okhttp3.OkHttpClient
import javax.sql.DataSource

/**
 * This is a noop implemenation of ScalabilityChecks for production use. In future, this will
 * be replaced with runtime checks for use in production.
 */
class NoopVitessScaleSafetyChecks(
  val okHttpClient: OkHttpClient,
  val moshi: Moshi,
  val config: DataSourceConfig,
  val startVitessService: StartVitessService
) : ScalabilityChecks {
  override fun decorate(dataSource: DataSource): DataSource {
    if (config.type != DataSourceType.VITESS) return dataSource
    val proxy = ProxyDataSource(dataSource)
    proxy.proxyConfig = ProxyConfig.Builder().build()
    return proxy
  }
}
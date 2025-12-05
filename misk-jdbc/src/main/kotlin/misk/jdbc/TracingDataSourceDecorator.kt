package misk.jdbc

import io.opentracing.Tracer
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import javax.sql.DataSource

/**
 * DataSource decorator that adds OpenTracing support to MySQL connections
 * when using AWS Secrets Manager driver (which can't use TracingDriver directly)
 */
class TracingDataSourceDecorator(
  private val tracer: Tracer?,
  private val config: DataSourceConfig
) : DataSourceDecorator {
  
  override fun decorate(dataSource: DataSource): DataSource {
    // Only apply tracing decoration for MySQL with AWS Secrets Manager
    if (config.type != DataSourceType.MYSQL || !config.mysql_use_aws_secret_for_credentials || tracer == null) {
      return dataSource
    }
    
    // Use datasource-proxy to add tracing at the DataSource level
    return ProxyDataSourceBuilder(dataSource)
      .name("mysql-aws-secrets-tracing")
      .multiline()
      .countQuery()
      .build()
  }
}

package misk.jdbc

interface DataSourceConnector {
  fun config(): DataSourceConfig
}

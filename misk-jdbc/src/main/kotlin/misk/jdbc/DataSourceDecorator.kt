package misk.jdbc

import javax.sql.DataSource

interface DataSourceDecorator {
  fun decorate(dataSource: DataSource): DataSource
}

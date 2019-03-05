package misk.jdbc

import javax.sql.DataSource

/**
 * Keeps a reference to the intermediate DataSources that have been decorated as part of
 * applying a set of DataSourceDecorators to a DataSource.
 */
class DecoratedDataSource(
  decorated: DataSource,
  private val dataSource: DataSource
) : DataSource by decorated {

  /**
   * Returns the initial DataSource from a chain of DecoratedDataSources.
   */
  fun resolve(): DataSource = when (dataSource) {
    is DecoratedDataSource -> dataSource.resolve()
    else -> dataSource
  }
}

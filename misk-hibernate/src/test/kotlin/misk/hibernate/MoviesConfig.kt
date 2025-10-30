package misk.hibernate

import misk.jdbc.DataSourceConfig
import misk.config.Config

internal data class MoviesConfig(
  val vitess_mysql_data_source: DataSourceConfig,
  val vitess_mysql_reader_data_source: DataSourceConfig? = null,
  val vitess_mysql_no_scatter_data_source: DataSourceConfig,
  val mysql_data_source: DataSourceConfig,
  val cockroachdb_data_source: DataSourceConfig,
  val postgresql_data_source: DataSourceConfig,
  val tidb_data_source: DataSourceConfig
) : Config

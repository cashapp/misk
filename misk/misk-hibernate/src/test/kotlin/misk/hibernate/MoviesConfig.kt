package misk.hibernate

import misk.config.Config
import misk.jdbc.DataSourceConfig

internal data class MoviesConfig(
  val vitess_mysql_data_source: DataSourceConfig,
  val mysql_data_source: DataSourceConfig,
  val cockroachdb_data_source: DataSourceConfig,
  val postgresql_data_source: DataSourceConfig,
  val tidb_data_source: DataSourceConfig
) : Config

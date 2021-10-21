package misk.hibernate

import misk.jdbc.DataSourceConfig
import wisp.config.Config

internal data class MoviesConfig(
  val vitess_mysql_data_source: DataSourceConfig,
  val mysql_data_source: DataSourceConfig,
  val cockroachdb_data_source: DataSourceConfig,
  val postgresql_data_source: DataSourceConfig,
  val tidb_data_source: DataSourceConfig,
  val gcp_spanner_data_source: DataSourceConfig,
) : Config


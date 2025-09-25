package misk.sqldelight

import misk.jdbc.DataSourceConfig
import misk.config.Config

internal data class SqlDelightTestConfig(
  val data_source: DataSourceConfig
) : Config

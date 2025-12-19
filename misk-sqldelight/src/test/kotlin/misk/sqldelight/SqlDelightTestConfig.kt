package misk.sqldelight

import misk.config.Config
import misk.jdbc.DataSourceConfig

internal data class SqlDelightTestConfig(val data_source: DataSourceConfig) : Config

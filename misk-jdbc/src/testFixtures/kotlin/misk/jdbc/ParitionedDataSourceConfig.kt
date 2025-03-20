package misk.jdbc

import misk.testing.updateForParallelTests

fun DataSourceConfig.updateForParallelTests(): DataSourceConfig = this.updateForParallelTests { config, partitionId ->
  config.copy(database = "${config.database}_$partitionId")
}

fun DataSourceClusterConfig.updateForParallelTests(): DataSourceClusterConfig = this.copy(
  writer = writer.updateForParallelTests(),
  reader = reader?.updateForParallelTests()
)

package misk.jdbc

import misk.logging.getLogger
import misk.testing.updateForParallelTests

private val logger = getLogger<DataSourceConfig>()

fun DataSourceConfig.updateForParallelTests(): DataSourceConfig =
  this.updateForParallelTests { config, partitionId ->
    val partitionedDatabaseName = "${config.database}_$partitionId"
    logger.info { "Test running in parallel - using $partitionedDatabaseName database" }
    config.copy(database = partitionedDatabaseName)
  }

fun DataSourceClusterConfig.updateForParallelTests(): DataSourceClusterConfig =
  this.copy(writer = writer.updateForParallelTests(), reader = reader?.updateForParallelTests())

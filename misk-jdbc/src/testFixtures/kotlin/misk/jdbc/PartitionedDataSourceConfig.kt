package misk.jdbc

import misk.logging.getLogger
import misk.testing.gradleTestWorkerId

private val logger = getLogger<DataSourceConfig>()

/** Isolates each Gradle test worker's database, including workers from different test tasks. */
fun DataSourceConfig.updateForParallelTests(): DataSourceConfig {
  val workerId = gradleTestWorkerId() ?: return this
  val partitionedDatabaseName = "${database}_worker_$workerId"
  logger.info { "Test worker $workerId - using $partitionedDatabaseName database" }
  return copy(database = partitionedDatabaseName)
}

fun DataSourceClusterConfig.updateForParallelTests(): DataSourceClusterConfig =
  this.copy(writer = writer.updateForParallelTests(), reader = reader?.updateForParallelTests())

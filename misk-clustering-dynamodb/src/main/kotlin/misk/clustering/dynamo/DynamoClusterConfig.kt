package misk.clustering.dynamo

import wisp.config.Config

data class DynamoClusterConfig @JvmOverloads constructor(
  val update_frequency: Long = 30,
  val stale_threshold: Long = 60,
) : Config

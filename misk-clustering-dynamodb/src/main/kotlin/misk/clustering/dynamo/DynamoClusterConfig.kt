package misk.clustering.dynamo

import wisp.config.Config

data class DynamoClusterConfig @JvmOverloads constructor(
  val update_frequency_seconds: Long = 30,
  val stale_threshold_seconds: Long = 60,
) : Config

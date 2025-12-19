package misk.clustering.dynamo

import misk.config.Config

data class DynamoClusterConfig
@JvmOverloads
constructor(
  val appName: String = System.getenv("SERVICE_NAME") ?: "<invalid-service-name>",
  var table_name: String = "$appName.misk-cluster-members",
  val update_frequency_seconds: Long = 30,
  val stale_threshold_seconds: Long = 60,
) : Config

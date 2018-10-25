package misk.clustering.zookeeper

import misk.config.Config

data class ZookeeperConfig(
  val zk_connect: String,
  val session_timeout_msecs: Int = 3000
) : Config
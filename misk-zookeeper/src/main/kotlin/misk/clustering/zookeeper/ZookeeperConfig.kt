package misk.clustering.zookeeper

import misk.config.Config
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig

data class ZookeeperConfig(
  val zk_connect: String,
  val session_timeout_msecs: Int = 3000,
  val cert_store: CertStoreConfig? = null,
  val trust_store: TrustStoreConfig? = null
) : Config

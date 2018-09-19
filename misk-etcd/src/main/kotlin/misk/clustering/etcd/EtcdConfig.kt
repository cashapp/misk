package misk.clustering.etcd

data class EtcdConfig(
  val endpoints: List<String>,
  val session_timeout_ms: Long = 10000
)
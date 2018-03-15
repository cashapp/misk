package misk.eventrouter

class AlphabeticalMapper : ClusterMapper {
  override fun topicToHost(clusterSnapshot: ClusterSnapshot, topic: String): String {
    return clusterSnapshot.hosts.sorted().first()
  }
}

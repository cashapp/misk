package misk.eventrouter

import com.google.common.collect.Ordering

/**
 * AlphabeticalMapper is used purely for testing.
 */
class AlphabeticalMapper : ClusterMapper {
  override fun topicToHost(clusterSnapshot: ClusterSnapshot, topic: String): String {
    return Ordering.natural<String>().min(clusterSnapshot.hosts)
  }
}

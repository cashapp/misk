package misk.eventrouter

import com.google.common.hash.Hashing

class ConsistentHashing : ClusterMapper {
  private val hashFunction = Hashing.murmur3_128()
  private val mod = 65536L
  private val virtualPoints = 16

  // TODO(tso): cache for each clusterSnapshot
  override fun topicToHost(clusterSnapshot: ClusterSnapshot, topic: String): String {
    val hosts = clusterSnapshot.hosts.sorted()

    val topicHash = Math.floorMod(hashFunction.hashString(topic, Charsets.UTF_8).padToLong(), mod)
    var shortestDistance = Long.MAX_VALUE
    var bestHost: String? = null

    for (host in hosts) {
      for (i in 0..virtualPoints) {
        val hostHash =
            Math.floorMod(hashFunction.hashString("$host $i", Charsets.UTF_8).padToLong(), mod)
        val distanceClockwiseTo = distanceClockwiseTo(hostHash, topicHash)
        if (distanceClockwiseTo < shortestDistance) {
          shortestDistance = distanceClockwiseTo
          bestHost = host
        }
      }
    }

    return bestHost ?: throw IllegalStateException("Could not find a host")
  }

  private fun distanceClockwiseTo(start: Long, finish: Long): Long {
    return Math.floorMod(finish - start, mod)
  }
}

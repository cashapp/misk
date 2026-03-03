package misk.eventrouter

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsistentHashing @Inject constructor(
  private val hashFunction: HashFunction = Hashing.murmur3_32(),
  private val mod: Long = 65536L,
  private val virtualPoints: Int = 16
) : ClusterMapper {
  // TODO(tso): make this more efficient
  override fun topicToHost(clusterSnapshot: ClusterSnapshot, topic: String): String {
    val hosts = clusterSnapshot.hosts.sorted()

    val topicHash = Math.floorMod(hashFunction.hashString(topic, Charsets.UTF_8).padToLong(), mod)
    var shortestDistance = Long.MAX_VALUE
    var bestHost: String? = null

    for (host in hosts) {
      for (i in 0 until virtualPoints) {
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

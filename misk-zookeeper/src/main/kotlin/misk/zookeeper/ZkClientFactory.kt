package misk.zookeeper

import misk.clustering.zookeeper.asZkNamespace
import org.apache.curator.framework.CuratorFramework

/**
 * Factory for generating a zookeeper client that's configured to read and
 * write data within the app's namespace.
 */
class ZkClientFactory constructor(
  appName: String,
  curator: CuratorFramework
) {
  internal val client = lazy { curator.usingNamespace("$SERVICES_NODE/${appName.asZkNamespace}/data") }

  fun client(): CuratorFramework {
    return client.value
  }
}

package misk.zookeeper

import misk.clustering.zookeeper.asZkNamespace
import misk.config.AppName
import org.apache.curator.framework.CuratorFramework
import javax.inject.Inject

/**
 * Factory for generating a zookeeper client that's configured to read and
 * write data within the app's namespace.
 */
class ZkClientFactory @Inject constructor(
  @AppName appName: String,
  curator: CuratorFramework
) {
  internal val client = lazy { curator.usingNamespace("$SERVICES_NODE/${appName.asZkNamespace}/data") }

  fun client(): CuratorFramework {
    return client.value
  }
}


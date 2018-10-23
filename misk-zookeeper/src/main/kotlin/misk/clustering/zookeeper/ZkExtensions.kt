package misk.clustering.zookeeper

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.imps.CuratorFrameworkState

/** @return a version of the path suitable for as a namespace (see [CuratorFramework.usingNamespace] */
val String.asZkNamespace: String get() = if (startsWith("/")) substring(1) else this

/** @return a version of the string suitable as a path */
val String.asZkPath: String get() = if (startsWith("/")) this else "/$this"

/** @return true if the client is running */
val CuratorFramework.isRunning: Boolean get() = state == CuratorFrameworkState.STARTED;

/** @return true if the client is connected to zk */
val CuratorFramework.isConnected: Boolean get() = zookeeperClient?.isConnected ?: false


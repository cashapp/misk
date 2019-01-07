package misk.clustering.zookeeper

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import javax.inject.Inject
import javax.inject.Provider

internal class CuratorFrameworkProvider @Inject internal constructor(
  private val config: ZookeeperConfig
) : Provider<CuratorFramework> {

  override fun get(): CuratorFramework {
    // Uses reasonable default values from http://curator.apache.org/getting-started.html
    val retryPolicy = ExponentialBackoffRetry(1000, 3)
    return CuratorFrameworkFactory.builder()
        .connectString(config.zk_connect)
        .retryPolicy(retryPolicy)
        .sessionTimeoutMs(config.session_timeout_msecs)
        .canBeReadOnly(false)
        .threadFactory(ThreadFactoryBuilder()
            .setNameFormat("zk-clustering-${config.zk_connect}")
            .build())
        .build()
  }
}

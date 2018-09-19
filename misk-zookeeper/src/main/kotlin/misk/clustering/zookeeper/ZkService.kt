package misk.clustering.zookeeper

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.logging.getLogger
import org.apache.curator.framework.CuratorFramework
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ZkService @Inject internal constructor(
  private val curatorFramework: CuratorFramework
) : AbstractIdleService(), DependentService {

  override val consumedKeys: Set<Key<*>> = setOf()
  override val producedKeys: Set<Key<*>> = setOf(ZookeeperModule.serviceKey)

  override fun startUp() {
    log.info { "starting connection to zookeeper" }
    curatorFramework.start()
  }

  override fun shutDown() {
    log.info { "closing connection to zookeeper" }
    curatorFramework.close()
  }

  companion object {
    private val log = getLogger<ZkService>()
  }
}
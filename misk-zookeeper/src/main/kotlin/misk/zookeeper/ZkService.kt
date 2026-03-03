package misk.zookeeper

import com.google.common.util.concurrent.AbstractIdleService
import misk.logging.getLogger
import org.apache.curator.framework.CuratorFramework

class ZkService internal constructor(
  private val curatorFramework: CuratorFramework
) : AbstractIdleService() {
  override fun startUp() {
    log.info { "starting connection to zookeeper" }
    curatorFramework.start()
  }

  override fun shutDown() {
    log.info { "closing connection to zookeeper" }

    try {
      curatorFramework.close()
    } catch (th: Throwable) {
      log.error(th) { "unable to close connection to zookeeper" }
    }
  }

  companion object {
    private val log = getLogger<ZkService>()
  }
}

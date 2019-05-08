package misk.zookeeper

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.inject.keyOf
import misk.logging.getLogger
import org.apache.curator.framework.CuratorFramework
import kotlin.reflect.KClass

class ZkService internal constructor(
  private val curatorFramework: CuratorFramework,
  qualifier: KClass<out Annotation>?
) : AbstractIdleService(), DependentService {

  override val consumedKeys: Set<Key<*>> = setOf()
  override val producedKeys: Set<Key<*>> = setOf(keyOf<ZkService>(qualifier))

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

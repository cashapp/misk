package misk.zookeeper

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.clustering.Cluster
import misk.clustering.zookeeper.Group
import misk.clustering.zookeeper.GroupManager
import misk.clustering.zookeeper.ZkGroupModule
import misk.clustering.zookeeper.ZkLeaseTestModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
class ZkGroupTest {

  @MiskTestModule private val module = Modules.combine(
      MiskTestingServiceModule(),
      ZkLeaseTestModule(),
      ZkGroupModule()
  )

  @Inject lateinit var groupManager: GroupManager
  @Inject lateinit var cluster: Cluster

  @Test fun asdf() {
    val group = groupManager.group("test")

    group.watch { before, after ->
      println("watching! $before -> $after")
      true
    }

    val self = cluster.snapshot.self

    group.join("hello!")
    group.waitUntil { members -> members.any { it.node == self && it.state == "hello!" } }
//    wait(group, "hello!")

    group.join("buh-bye")
    group.waitUntil { members -> members.any { it.node == self && it.state == "buh-bye" } }
//    wait(group, "buh-bye")

    group.leave()
    group.waitUntil { members -> members.none { it.node == self } }
//    waitGone(group)

    group.join("hello again!")
    group.waitUntil { members -> members.any { it.node == self && it.state == "hello again!" } }
  }

  fun wait(group: Group, state: String) {
    val self = cluster.snapshot.self
    val found = CountDownLatch(1)
    group.watch { _, after ->
      val member = after.firstOrNull { it.node == self && it.state == state }
      member?.let { found.countDown() }
      member == null
    }

    assertThat(found.await(5, TimeUnit.SECONDS)).isTrue()
  }

  fun waitGone(group: Group) {
    val self = cluster.snapshot.self
    val gone = CountDownLatch(1)
    group.watch { _, after ->
      if (after.none { it.node == self }) {
        gone.countDown()
        false
      } else {
        true
      }
    }

    assertThat(gone.await(5, TimeUnit.SECONDS)).isTrue()
  }
}

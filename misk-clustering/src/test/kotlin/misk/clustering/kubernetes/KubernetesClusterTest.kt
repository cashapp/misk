package misk.clustering.kubernetes

import com.google.inject.Module
import com.google.inject.util.Modules
import io.kubernetes.client.openapi.models.V1ContainerStatus
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodStatus
import io.kubernetes.client.util.Watches
import misk.MiskTestingServiceModule
import misk.clustering.Cluster
import misk.clustering.ClusterHashRing
import misk.clustering.DefaultCluster
import misk.clustering.kubernetes.KubernetesClusterWatcher.Companion.CHANGE_TYPE_ADDED
import misk.clustering.kubernetes.KubernetesClusterWatcher.Companion.CHANGE_TYPE_DELETED
import misk.clustering.kubernetes.KubernetesClusterWatcher.Companion.CHANGE_TYPE_MODIFIED
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class KubernetesClusterTest {
  @MiskTestModule val module: Module = Modules.combine(
    MiskTestingServiceModule(),
    object : KAbstractModule() {
      override fun configure() {
        install(
          KubernetesClusterModule(
            KubernetesConfig(
              my_pod_namespace = TEST_NAMESPACE,
              my_pod_name = TEST_SELF_NAME,
              my_pod_ip = TEST_SELF_IP
            )
          )
        )
      }
    }
  )

  @Inject private lateinit var cluster: DefaultCluster

  @Test fun startsWithSelfNotReady() {
    val self = cluster.snapshot.self
    assertThat(self.ipAddress).isEqualTo(TEST_SELF_IP)
    assertThat(self.name).isEqualTo(TEST_SELF_NAME)
    assertThat(cluster.snapshot.selfReady).isEqualTo(false)
  }

  @Test fun startsWithNoReadyMembers() {
    assertThat(cluster.snapshot.readyMembers).isEmpty()
  }

  @Test fun selfReadyNotReady() {
    val ready = CountDownLatch(1)

    val changes = mutableListOf<Cluster.Changes>()
    cluster.watch { changes.add(it) }
    handleWatch(CHANGE_TYPE_ADDED, newPod(TEST_SELF_NAME, true, TEST_SELF_IP))
    handleWatch(CHANGE_TYPE_MODIFIED, newPod(TEST_SELF_NAME, false, TEST_SELF_IP))
    cluster.syncPoint { ready.countDown() }

    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(),
          resourceMapper = ClusterHashRing(setOf())
        )
      ),
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = true,
          readyMembers = setOf(expectedSelf),
          resourceMapper = ClusterHashRing(setOf(expectedSelf))
        ),
        added = setOf(expectedSelf)
      ),
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(),
          resourceMapper = ClusterHashRing(setOf())
        ),
        removed = setOf(expectedSelf)
      )
    )
  }

  @Test fun memberAddedIfReadyAndIPAddressAssigned() {
    val ready = CountDownLatch(1)

    val changes = mutableListOf<Cluster.Changes>()
    cluster.watch { changes.add(it) }
    handleWatch("ADDED", newPod("larry-blerp", true, "10.0.0.3"))
    handleWatch("ADDED", newPod("larry-blerp2", true, "10.0.0.4"))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(),
          resourceMapper = ClusterHashRing(setOf())
        )
      ),
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(Cluster.Member("larry-blerp", "10.0.0.3")),
          resourceMapper = ClusterHashRing(setOf(Cluster.Member("larry-blerp", "10.0.0.3")))
        ),
        added = setOf(Cluster.Member("larry-blerp", "10.0.0.3"))
      ),
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(
            Cluster.Member("larry-blerp", "10.0.0.3"),
            Cluster.Member("larry-blerp2", "10.0.0.4")
          ),
          resourceMapper = ClusterHashRing(
            setOf(
              Cluster.Member("larry-blerp", "10.0.0.3"),
              Cluster.Member("larry-blerp2", "10.0.0.4")
            )
          )
        ),
        added = setOf(Cluster.Member("larry-blerp2", "10.0.0.4"))
      )
    )
  }

  @Test fun memberRemovedIfDeleted() {
    val changes = mutableListOf<Cluster.Changes>()
    val ready = CountDownLatch(1)

    // Start with members
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp", true, "10.0.0.3"))
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp2", true, "10.0.0.4"))

    // Explicitly remove a member
    cluster.watch { changes.add(it) }
    handleWatch(CHANGE_TYPE_DELETED, newPod("larry-blerp"))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(
            Cluster.Member("larry-blerp", "10.0.0.3"),
            Cluster.Member("larry-blerp2", "10.0.0.4")
          ),
          resourceMapper = ClusterHashRing(
            setOf(
              Cluster.Member("larry-blerp", "10.0.0.3"),
              Cluster.Member("larry-blerp2", "10.0.0.4")
            )
          )
        )
      ),
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(Cluster.Member("larry-blerp2", "10.0.0.4")),
          resourceMapper = ClusterHashRing(
            setOf(
              Cluster.Member("larry-blerp2", "10.0.0.4")
            )
          )
        ),
        removed = setOf(Cluster.Member("larry-blerp", ""))
      )
    )
  }

  @Test fun memberNotAddedIfNotReady() {
    val changes = mutableListOf<Cluster.Changes>()
    val ready = CountDownLatch(1)

    cluster.watch { changes.add(it) }
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp", false, "10.0.0.3"))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    // Not ready, so shouldn't be added or marked as removed
    assertThat(changes).containsExactly(
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(),
          resourceMapper = ClusterHashRing(setOf())
        )
      )
    )
  }

  @Test fun memberNotAddedIfNoIPAddressAssigned() {
    val changes = mutableListOf<Cluster.Changes>()
    val ready = CountDownLatch(1)

    cluster.watch { changes.add(it) }
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp", true))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    // No IP address, so shouldn't be added or marked as removed
    assertThat(changes).containsExactly(
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(),
          resourceMapper = ClusterHashRing(setOf())
        )
      )
    )
  }

  @Test fun memberRemovedIfTransitionsToNotReady() {
    val ready = CountDownLatch(1)
    val changes = mutableListOf<Cluster.Changes>()

    // Start as an existing member
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp", true, "10.0.0.3"))
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp2", true, "10.0.0.4"))

    // Transition to not ready - should remove from the list
    cluster.watch { changes.add(it) }
    handleWatch(CHANGE_TYPE_MODIFIED, newPod("larry-blerp", false, "10.0.0.3"))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(
            Cluster.Member("larry-blerp", "10.0.0.3"),
            Cluster.Member("larry-blerp2", "10.0.0.4")
          ),
          resourceMapper = ClusterHashRing(
            setOf(
              Cluster.Member("larry-blerp", "10.0.0.3"),
              Cluster.Member("larry-blerp2", "10.0.0.4")
            )
          )
        )
      ),
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(Cluster.Member("larry-blerp2", "10.0.0.4")),
          resourceMapper = ClusterHashRing(
            setOf(
              Cluster.Member("larry-blerp2", "10.0.0.4")
            )
          )
        ),
        removed = setOf(Cluster.Member("larry-blerp", "10.0.0.3"))
      )
    )
  }

  @Test fun memberRemovedIfIPAddressLost() {
    val ready = CountDownLatch(1)
    val changes = mutableListOf<Cluster.Changes>()

    // Start as an existing member
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp", true, "10.0.0.3"))
    handleWatch(CHANGE_TYPE_ADDED, newPod("larry-blerp2", true, "10.0.0.4"))

    // Transition to no IP address
    cluster.watch { changes.add(it) }
    handleWatch(CHANGE_TYPE_MODIFIED, newPod("larry-blerp", true))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(
            Cluster.Member("larry-blerp", "10.0.0.3"),
            Cluster.Member("larry-blerp2", "10.0.0.4")
          ),
          resourceMapper = ClusterHashRing(
            setOf(
              Cluster.Member("larry-blerp", "10.0.0.3"),
              Cluster.Member("larry-blerp2", "10.0.0.4")
            )
          )
        )
      ),
      Cluster.Changes(
        snapshot = Cluster.Snapshot(
          self = expectedSelf,
          selfReady = false,
          readyMembers = setOf(Cluster.Member("larry-blerp2", "10.0.0.4")),
          resourceMapper = ClusterHashRing(
            setOf(
              Cluster.Member("larry-blerp2", "10.0.0.4")
            )
          )
        ),
        removed = setOf(Cluster.Member("larry-blerp", ""))
      )
    )
  }

  private fun handleWatch(type: String, pod: V1Pod) {
    Watches.newResponse(type, pod).applyTo(cluster)
  }

  private fun newPod(name: String, isReady: Boolean = false, ipAddress: String? = null): V1Pod {
    val containerStatus = V1ContainerStatus()
    containerStatus.ready = isReady
    containerStatus.name = TEST_NAMESPACE

    val pod = V1Pod()
    pod.metadata = V1ObjectMeta()
    pod.metadata!!.namespace = TEST_NAMESPACE
    pod.metadata!!.name = name
    pod.status = V1PodStatus()
    pod.status!!.containerStatuses = listOf(containerStatus)
    pod.status!!.podIP = ipAddress
    return pod
  }

  companion object {
    const val TEST_NAMESPACE = "larry"
    const val TEST_SELF_NAME = "larry-76485b7568-l5rmm"
    const val TEST_SELF_IP = "10.133.66.206"

    val expectedSelf = Cluster.Member(TEST_SELF_NAME, TEST_SELF_IP)
  }
}

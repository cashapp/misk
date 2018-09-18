package misk.clustering.kubernetes

import io.kubernetes.client.models.V1ContainerStatus
import io.kubernetes.client.models.V1ObjectMeta
import io.kubernetes.client.models.V1Pod
import io.kubernetes.client.models.V1PodStatus
import io.kubernetes.client.util.Watches
import misk.clustering.Cluster
import misk.clustering.kubernetes.KubernetesClusterService.Companion.CHANGE_TYPE_ADDED
import misk.clustering.kubernetes.KubernetesClusterService.Companion.CHANGE_TYPE_DELETED
import misk.clustering.kubernetes.KubernetesClusterService.Companion.CHANGE_TYPE_MODIFIED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class KubernetesClusterServiceTest {
  lateinit var cluster: KubernetesClusterService

  @BeforeEach
  fun initService() {
    cluster = KubernetesClusterService(config = KubernetesConfig(
        my_pod_namespace = TEST_NAMESPACE,
        my_pod_name = "larry-76485b7568-l5rmm",
        my_pod_ip = "10.133.66.206"
    ))

    cluster.startAsync()
    cluster.awaitRunning(5, TimeUnit.SECONDS)
  }

  @AfterEach
  fun stopBackgroundThread() {
    cluster.stopAsync()
    cluster.awaitTerminated(5, TimeUnit.SECONDS)
  }

  @Test fun testSelf() {
    val self = cluster.self
    assertThat(self.ipAddress).isEqualTo("10.133.66.206")
    assertThat(self.name).isEqualTo("larry-76485b7568-l5rmm")
  }

  @Test fun testEmptyPeers() {
    assertThat(cluster.peers).isEmpty()
  }

  @Test fun peerAddedIfReadyAndIPAddressAssigned() {
    val ready = CountDownLatch(1)

    val changes = mutableListOf<Cluster.Changes>()
    cluster.watch { changes.add(it) }
    cluster.clusterChanged(Watches.newResponse("ADDED", newPod("larry-blerp", true, "10.0.0.3")))
    cluster.clusterChanged(Watches.newResponse("ADDED", newPod("larry-blerp2", true, "10.0.0.4")))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
        Cluster.Changes(current = setOf()),
        Cluster.Changes(
            current = setOf(Cluster.Member("larry-blerp", "10.0.0.3")),
            added = setOf(Cluster.Member("larry-blerp", "10.0.0.3"))),
        Cluster.Changes(
            current = setOf(
                Cluster.Member("larry-blerp", "10.0.0.3"),
                Cluster.Member("larry-blerp2", "10.0.0.4")),
            added = setOf(Cluster.Member("larry-blerp2", "10.0.0.4"))))
  }

  @Test fun peerRemoved() {
    val changes = mutableListOf<Cluster.Changes>()
    val ready = CountDownLatch(1)

    // Start with members
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp", true, "10.0.0.3")))
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp2", true, "10.0.0.4")))

    // Explicitly remove a member
    cluster.watch { changes.add(it) }
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_DELETED, newPod("larry-blerp")))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
        Cluster.Changes(current = setOf(
            Cluster.Member("larry-blerp", "10.0.0.3"),
            Cluster.Member("larry-blerp2", "10.0.0.4"))),
        Cluster.Changes(
            current = setOf(Cluster.Member("larry-blerp2", "10.0.0.4")),
            removed = setOf(Cluster.Member("larry-blerp", ""))))
  }

  @Test fun peerNotAddedIfNotReady() {
    val changes = mutableListOf<Cluster.Changes>()
    val ready = CountDownLatch(1)

    cluster.watch { changes.add(it) }
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp", false, "10.0.0.3")))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    // Not ready, so shouldn't be added or marked as removed
    assertThat(changes).containsExactly(Cluster.Changes(current = setOf()))
  }

  @Test fun peerNotAddedIfNoIPAddressAssigned() {
    val changes = mutableListOf<Cluster.Changes>()
    val ready = CountDownLatch(1)

    cluster.watch { changes.add(it) }
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp", true)))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    // No IP address, so shouldn't be added or marked as removed
    assertThat(changes).containsExactly(Cluster.Changes(current = setOf()))
  }

  @Test fun peerRemovedIfTransitionsToNotReady() {
    val ready = CountDownLatch(1)
    val changes = mutableListOf<Cluster.Changes>()

    // Start as an existing member
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp", true, "10.0.0.3")))
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp2", true, "10.0.0.4")))

    // Transition to not ready - should remove from the list
    cluster.watch { changes.add(it) }
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_MODIFIED, newPod("larry-blerp", false, "10.0.0.3")))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
        Cluster.Changes(current = setOf(
            Cluster.Member("larry-blerp", "10.0.0.3"),
            Cluster.Member("larry-blerp2", "10.0.0.4"))),
        Cluster.Changes(
            current = setOf(Cluster.Member("larry-blerp2", "10.0.0.4")),
            removed = setOf(Cluster.Member("larry-blerp", "10.0.0.3"))))
  }

  @Test fun peerRemovedIfIPAddressLost() {
    val ready = CountDownLatch(1)
    val changes = mutableListOf<Cluster.Changes>()

    // Start as an existing member
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp", true, "10.0.0.3")))
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_ADDED, newPod("larry-blerp2", true, "10.0.0.4")))

    // Transition to no IP address
    cluster.watch { changes.add(it) }
    cluster.clusterChanged(Watches.newResponse(CHANGE_TYPE_MODIFIED, newPod("larry-blerp", true)))
    cluster.syncPoint { ready.countDown() }
    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()

    assertThat(changes).containsExactly(
        Cluster.Changes(current = setOf(
            Cluster.Member("larry-blerp", "10.0.0.3"),
            Cluster.Member("larry-blerp2", "10.0.0.4"))),
        Cluster.Changes(
            current = setOf(Cluster.Member("larry-blerp2", "10.0.0.4")),
            removed = setOf(Cluster.Member("larry-blerp", ""))))
  }

  private fun newPod(name: String, isReady: Boolean = false, ipAddress: String? = null): V1Pod {
    val containerStatus = V1ContainerStatus()
    containerStatus.isReady = isReady
    containerStatus.name = TEST_NAMESPACE

    val pod = V1Pod()
    pod.metadata = V1ObjectMeta()
    pod.metadata.namespace = TEST_NAMESPACE
    pod.metadata.name = name
    pod.status = V1PodStatus()
    pod.status.containerStatuses = listOf(containerStatus)
    pod.status.podIP = ipAddress
    return pod
  }

  companion object {
    const val TEST_NAMESPACE = "larry"
  }
}
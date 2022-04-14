package misk.clustering.fake

import misk.clustering.Cluster
import misk.clustering.ClusterService
import misk.clustering.ClusterWatch
import misk.clustering.DefaultCluster
import wisp.logging.getLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [FakeCluster] is a [Cluster] that is a fake. It delegates entirely to [DefaultCluster],
 * but allows us to keep [DefaultCluster] internal and make it clear that [FakeCluster]
 * is only intended to be used for fakes.
 *
 * NB(mmihic): I'd prefer for this to be in the misk-testing module, but it can't since it
 * relies on [DefaultCluster] and we want to leave [DefaultCluster] internal
 */
@Singleton
class FakeCluster internal constructor(
  val resourceMapper: ExplicitClusterResourceMapper,
  private val delegate: DefaultCluster
) : ClusterService by delegate, Cluster by delegate {
  constructor(resourceMapper: ExplicitClusterResourceMapper) :
    this(resourceMapper, DefaultCluster(self) { resourceMapper })

  @Inject constructor() : this(
    ExplicitClusterResourceMapper().apply {
      setDefaultMapping(self)
    }
  )

  override fun watch(watch: ClusterWatch) {
    waitFor {
      delegate.watch(watch)
    }
  }

  fun clusterChanged(
    membersBecomingReady: Set<Cluster.Member> = setOf(),
    membersBecomingNotReady: Set<Cluster.Member> = setOf()
  ) {
    waitFor {
      delegate.clusterChanged(membersBecomingReady, membersBecomingNotReady)
    }
  }

  private fun waitFor(f: () -> Unit) {
    // Single thread all changes to the cluster by waiting for each operation to complete
    val latch = CountDownLatch(1)
    f()
    delegate.syncPoint {
      latch.countDown()
    }
    check(latch.await(5, TimeUnit.SECONDS)) { "cluster change did not complete within 5 seconds " }
  }

  companion object {
    const val SELF_NAME = "fake-self-node"
    const val SELF_IP = "10.0.0.1"
    @JvmStatic val self = Cluster.Member(name = SELF_NAME, ipAddress = SELF_IP)

    private val log = getLogger<FakeCluster>()
  }
}

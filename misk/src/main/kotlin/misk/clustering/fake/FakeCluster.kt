package misk.clustering.fake

import misk.DependentService
import misk.clustering.Cluster
import misk.clustering.ClusterWatch
import misk.clustering.DefaultCluster
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
) : Cluster by delegate, DependentService by delegate {

  constructor(resourceMapper: ExplicitClusterResourceMapper) :
      this(resourceMapper, DefaultCluster(Cluster.Member(SELF_NAME, SELF_IP)) { resourceMapper })

  constructor() : this(ExplicitClusterResourceMapper())

  override fun watch(watch: ClusterWatch) = delegate.watch(watch)

  fun clusterChanged(
    membersBecomingReady: Set<Cluster.Member> = setOf(),
    membersBecomingNotReady: Set<Cluster.Member> = setOf()
  ) = delegate.clusterChanged(membersBecomingReady, membersBecomingNotReady)

  fun syncPoint(callback: () -> Unit) = delegate.syncPoint(callback)

  companion object {
    const val SELF_NAME = "fake-self-node"
    const val SELF_IP = "10.0.0.1"
  }
}
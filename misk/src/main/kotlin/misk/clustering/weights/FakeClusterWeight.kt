package misk.clustering.weights

import misk.inject.KAbstractModule

/**
 * A [ClusterWeightProvider] for testing
 */
class FakeClusterWeight : ClusterWeightProvider {

  private var weight = 100

  override fun get(): Int {
    return weight
  }

  fun setClusterWeight(weight: Int) {
    this.weight = weight
  }
}

/**
 * Provides a [FakeClusterWeight] for testing
 */
class FakeClusterWeightModule : KAbstractModule() {
  override fun configure() {
    val fake = FakeClusterWeight()
    bind<FakeClusterWeight>().toInstance(fake)
    bind<ClusterWeightProvider>().toInstance(fake)
    install(NoOpClusterWeightServiceModule())
  }
}

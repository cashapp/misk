package misk.clustering.weights

import misk.inject.KAbstractModule
import misk.testing.FakeFixture
import misk.testing.TestFixture

/**
 * A [ClusterWeightProvider] for testing
 */
class FakeClusterWeight : ClusterWeightProvider, FakeFixture() {

  private var weight by resettable { 100 }

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
    multibind<TestFixture>().to<FakeClusterWeight>()
    bind<ClusterWeightProvider>().toInstance(fake)
    install(NoOpClusterWeightServiceModule())
  }
}

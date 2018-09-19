package misk.clustering.etcd

import misk.MiskServiceModule
import misk.clustering.fake.FakeCluster
import misk.config.Config
import misk.config.ConfigModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class EtcdLeaseTest {
  @MiskTestModule val module = TestModule()
  @Inject private lateinit var cluster: FakeCluster

  @Test fun leaseNotHeldIfNotMappedToSelf() {

  }

  @Test fun failGracefullyIfLeaseHeldBySomeoneElse() {
  }

  @Test fun leaseReleasedIfNoLongerMappedToSelf() {
  }

  data class EmptyConfig(val enabled: Boolean = true) : Config

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(ConfigModule.create("my-app", EmptyConfig()))
      install(MiskServiceModule())
      install(EtcdTestModule())
    }
  }
}
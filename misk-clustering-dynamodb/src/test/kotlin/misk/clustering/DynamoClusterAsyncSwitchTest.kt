package misk.clustering

import jakarta.inject.Inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import misk.MiskTestingServiceModule
import misk.aws2.dynamodb.testing.DynamoDbTable
import misk.aws2.dynamodb.testing.InProcessDynamoDbModule
import misk.clustering.dynamo.DyClusterMember
import misk.clustering.dynamo.DynamoClusterConfig
import misk.clustering.dynamo.DynamoClusterModule
import misk.clustering.dynamo.DynamoClusterWatcherTask
import misk.clustering.weights.FakeClusterWeightModule
import misk.inject.FakeSwitch
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoClusterAsyncSwitchTest {
  @MiskTestModule
  private val module =
    object : KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        install(FakeClusterWeightModule())
        install(InProcessDynamoDbModule(DynamoDbTable(TEST_TABLE_NAME, DyClusterMember::class)))
        install(DynamoClusterModule(DynamoClusterConfig(appName = TEST_SERVICE_NAME)))
        bind<FakeSwitch>().asSingleton()
        bindOptionalBinding<misk.inject.AsyncSwitch>().to<FakeSwitch>()
        multibind<TestFixture>().to<FakeSwitch>()
      }
    }

  @Inject private lateinit var cluster: DefaultCluster
  @Inject private lateinit var dynamoClusterWatcherTask: DynamoClusterWatcherTask
  @Inject private lateinit var fakeSwitch: FakeSwitch

  @BeforeEach
  fun setUp() {
    fakeSwitch.enabledKeys.add("clustering")
  }

  @Test
  fun `cluster watcher removes from cluster when async switch is disabled and re-registers when re-enabled`() {
    waitFor { dynamoClusterWatcherTask.run() }
    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    fakeSwitch.enabledKeys.remove("clustering")

    waitFor { dynamoClusterWatcherTask.run() }
    assertThat(cluster.snapshot.readyMembers).hasSize(0)

    fakeSwitch.enabledKeys.add("clustering")

    waitFor { dynamoClusterWatcherTask.run() }
    assertThat(cluster.snapshot.readyMembers).hasSize(1)
  }

  private fun waitFor(f: () -> Unit) {
    val latch = CountDownLatch(1)
    f()
    cluster.syncPoint { latch.countDown() }
    check(latch.await(5, TimeUnit.SECONDS)) { "cluster change did not complete within 5 seconds" }
  }

  companion object {
    const val TEST_SERVICE_NAME = "test-service"
    const val TEST_TABLE_NAME = "$TEST_SERVICE_NAME.misk-cluster-members"
  }
}

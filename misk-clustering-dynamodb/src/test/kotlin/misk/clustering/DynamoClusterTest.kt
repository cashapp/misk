package misk.clustering

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.aws2.dynamodb.testing.DynamoDbTable
import misk.aws2.dynamodb.testing.InProcessDynamoDbModule
import misk.clustering.dynamo.DyClusterMember
import misk.clustering.dynamo.DynamoClusterModule
import misk.clustering.dynamo.DynamoClusterWatcherTask
import misk.clustering.weights.FakeClusterWeightModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

private class TestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(FakeClusterWeightModule())
    install(
      InProcessDynamoDbModule(
        DynamoDbTable("misk-cluster-members", DyClusterMember::class),
      )
    )
    install(DynamoClusterModule())
  }
}

@MiskTest(startService = true)
class DynamoClusterTest {
  @MiskTestModule
  private val module = TestModule()

  @Inject private lateinit var cluster: Cluster
  @Inject private lateinit var dynamoClusterWatcherTask: DynamoClusterWatcherTask
  @Inject private lateinit var fakeClock: FakeClock

  @Test
  fun basic() {
    assertThat(cluster.snapshot.readyMembers).hasSize(0)
    dynamoClusterWatcherTask.updateOurselfInDynamo()
    dynamoClusterWatcherTask.recordCurrentDynamoCluster()
    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    fakeClock.add(Duration.ofSeconds(45))
    dynamoClusterWatcherTask.recordCurrentDynamoCluster()
    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    // Exceeded 1minute threshold, no longer considered part of cluster.
    fakeClock.add(Duration.ofSeconds(30))
    dynamoClusterWatcherTask.recordCurrentDynamoCluster()
    assertThat(cluster.snapshot.readyMembers).hasSize(0)

    // Refreshed, now part of cluster again.
    dynamoClusterWatcherTask.updateOurselfInDynamo()
    dynamoClusterWatcherTask.recordCurrentDynamoCluster()
    assertThat(cluster.snapshot.readyMembers).hasSize(1)
  }
}

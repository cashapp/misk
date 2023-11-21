package misk.clustering

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.aws2.dynamodb.testing.DynamoDbTable
import misk.aws2.dynamodb.testing.InProcessDynamoDbModule
import misk.clustering.dynamo.DyClusterMember
import misk.clustering.dynamo.DynamoClusterConfig
import misk.clustering.dynamo.DynamoClusterModule
import misk.clustering.dynamo.DynamoClusterWatcherTask
import misk.clustering.weights.FakeClusterWeight
import misk.clustering.weights.FakeClusterWeightModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Duration

@MiskTest(startService = true)
class DynamoClusterTest {
  @MiskTestModule
  private val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(FakeClusterWeightModule())
      install(
        InProcessDynamoDbModule(
          DynamoDbTable("misk-cluster-members", DyClusterMember::class),
        )
      )
      install(DynamoClusterModule(DynamoClusterConfig()))
    }
  }

  @Inject private lateinit var clock: FakeClock
  @Inject private lateinit var cluster: Cluster
  @Inject private lateinit var ddb: DynamoDbClient
  @Inject private lateinit var dynamoClusterWatcherTask: DynamoClusterWatcherTask
  @Inject private lateinit var fakeClusterWeight: FakeClusterWeight

  @Test
  fun basic() {
    assertThat(cluster.snapshot.readyMembers).hasSize(0)
    dynamoClusterWatcherTask.run()
    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    clock.add(Duration.ofSeconds(45))
    dynamoClusterWatcherTask.recordCurrentDynamoCluster()
    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    // Exceeded 1minute threshold, no longer considered part of cluster.
    clock.add(Duration.ofSeconds(30))
    dynamoClusterWatcherTask.recordCurrentDynamoCluster()
    assertThat(cluster.snapshot.readyMembers).hasSize(0)

    // Refreshed, now part of cluster again.
    dynamoClusterWatcherTask.run()
    assertThat(cluster.snapshot.readyMembers).hasSize(1)
  }

  @Test
  fun moreThan100Entries() {
    // Testing pagination
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(ddb)
      .build()
    val table = enhancedClient.table("misk-cluster-members", DynamoClusterWatcherTask.TABLE_SCHEMA)

    for (i in 0..150) {
      val member = DyClusterMember()
      member.name = "pod-${i}"
      member.updated_at = clock.instant().toEpochMilli()
      table.putItem(member)
    }
    dynamoClusterWatcherTask.run()
    assertThat(cluster.snapshot.readyMembers).hasSize(152)
  }

  @Test
  fun inactiveNodeDoesntRecordItself() {
    fakeClusterWeight.setClusterWeight(0)
    dynamoClusterWatcherTask.run()
    assertThat(cluster.snapshot.readyMembers).hasSize(0)
  }

  @Test
  fun resourceMapperStillWorks() {
    dynamoClusterWatcherTask.run()
    val self = cluster.snapshot.self
    assertThat(cluster.snapshot.resourceMapper["a"]).isEqualTo(self)
  }
}

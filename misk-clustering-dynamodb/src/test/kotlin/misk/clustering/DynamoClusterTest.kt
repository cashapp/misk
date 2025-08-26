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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MiskTest(startService = true)
class DynamoClusterTest {
  @MiskTestModule
  private val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(FakeClusterWeightModule())
      install(
        InProcessDynamoDbModule(
          DynamoDbTable(TEST_TABLE_NAME, DyClusterMember::class),
        )
      )
      install(DynamoClusterModule(DynamoClusterConfig(appName = TEST_SERVICE_NAME)))
    }
  }

  @Inject private lateinit var clock: FakeClock
  @Inject private lateinit var cluster: DefaultCluster
  @Inject private lateinit var ddb: DynamoDbClient
  @Inject private lateinit var dynamoClusterWatcherTask: DynamoClusterWatcherTask
  @Inject private lateinit var fakeClusterWeight: FakeClusterWeight

  @Test
  fun basic() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(ddb)
      .build()
    val table = enhancedClient.table(
      TEST_TABLE_NAME,
      DynamoClusterWatcherTask.TABLE_SCHEMA
    )

    assertThat(cluster.snapshot.readyMembers).hasSize(0)
    waitFor { dynamoClusterWatcherTask.run() }

    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    clock.add(Duration.ofSeconds(45))
    waitFor { dynamoClusterWatcherTask.recordCurrentDynamoCluster() }
    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    // Exceeded 1minute threshold, no longer considered part of cluster.
    clock.add(Duration.ofSeconds(30))
    waitFor { dynamoClusterWatcherTask.recordCurrentDynamoCluster() }
    assertThat(cluster.snapshot.readyMembers).hasSize(0)

    // Refreshed, now part of cluster again.
    waitFor { dynamoClusterWatcherTask.run() }
    assertThat(cluster.snapshot.readyMembers).hasSize(1)
    cluster.snapshot .readyMembers.single()

    // Confirm that on task shutdown, the pod is removed from the cluster
    assertThat(table.scan().items().stream().findAny().isEmpty).isEqualTo(false)
    dynamoClusterWatcherTask.stopAsync()
    dynamoClusterWatcherTask.awaitTerminated()
    assertThat(table.scan().items().stream().findAny().isEmpty).isEqualTo(true)
  }

  @Test
  fun watch() {
    val changes = mutableListOf<Cluster.Changes>()
    cluster.watch { changes.add(it) }
    waitFor { dynamoClusterWatcherTask.run() }
    assertThat(changes).hasSize(2)
    assertThat(changes.last().snapshot.readyMembers).hasSize(1)
  }

  @Test
  fun moreThan100Entries() {
    // Testing pagination
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(ddb)
      .build()
    val table = enhancedClient.table(TEST_TABLE_NAME, DynamoClusterWatcherTask.TABLE_SCHEMA)

    for (i in 0..150) {
      val member = DyClusterMember()
      member.name = "pod-${i}"
      member.updated_at = clock.instant().toEpochMilli()
      member.expires_at = clock.instant().plus(Duration.ofDays(1)).toEpochMilli() / 1000
      table.putItem(member)
    }
    waitFor { dynamoClusterWatcherTask.run() }
    assertThat(cluster.snapshot.readyMembers).hasSize(152)
  }

  @Test
  fun inspectAddedRecords() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(ddb)
      .build()
    val table = enhancedClient.table(
      TEST_TABLE_NAME,
      DynamoClusterWatcherTask.TABLE_SCHEMA
    )

    waitFor { dynamoClusterWatcherTask.run() }
    assertThat(cluster.snapshot.readyMembers).hasSize(1)

    val expiredTimeUnit = clock.instant().plus(Duration.ofDays(1)).toEpochMilli() / 1000

    // Verify that all records expire in 24 hours!
    assertTrue(
      table.scan().items()
        .all { m -> m.expires_at!! <= expiredTimeUnit && m.expires_at!! > m.updated_at!! / 1000 })
  }

  @Test
  fun inactiveNodeDoesntRecordItself() {
    fakeClusterWeight.setClusterWeight(0)
    waitFor {  dynamoClusterWatcherTask.run() }
    assertThat(cluster.snapshot.readyMembers).hasSize(0)
  }

  @Test
  fun resourceMapperStillWorks() {
    waitFor {  dynamoClusterWatcherTask.run() }
    val self = cluster.snapshot.self
    assertThat(cluster.snapshot.resourceMapper["a"]).isEqualTo(self)
  }
  private fun waitFor(f: () -> Unit) {
    val latch = CountDownLatch(1)
    f()
    cluster.syncPoint {
      latch.countDown()
    }
    check(latch.await(5, TimeUnit.SECONDS)) { "cluster change did not complete within 5 seconds " }
  }

  companion object {
    const val TEST_SERVICE_NAME = "test-service"
    const val TEST_TABLE_NAME = "$TEST_SERVICE_NAME.misk-cluster-members"
  }
}

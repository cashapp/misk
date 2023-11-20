package misk.clustering.dynamo

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.clustering.Cluster.Member
import misk.clustering.weights.ClusterWeightProvider
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import misk.time.timed
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues.numberValue
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import wisp.logging.getLogger
import java.time.Clock
import java.time.Duration


/**
 * This task does two things:
 * 1. Put our identifier into the dynamodb table so that others know about us
 * 2. Read the dynamodb table so that we know about others and updates the Cluster
 */
@Singleton
internal class DynamoClusterWatcherTask @Inject constructor(
  @ForDynamoDbClusterWatching private val taskQueue: RepeatedTaskQueue,
  ddb: DynamoDbClient,
  private val clock: Clock,
  private val clusterWeightProvider: ClusterWeightProvider,
  private val dynamoCluster: DynamoCluster,
) : AbstractIdleService() {
  private val enhancedClient = DynamoDbEnhancedClient.builder()
    .dynamoDbClient(ddb)
    .build()
  private val table = enhancedClient.table("misk-cluster-members", TABLE_SCHEMA)

  override fun startUp() {
    taskQueue.scheduleWithBackoff(timeBetweenRuns = Duration.ofSeconds(30)) {
      // If we're not active, we don't want to mark ourselves as part of the active cluster.
      if (clusterWeightProvider.get() > 0) {
        updateOurselfInDynamo()
      }
      recordCurrentDynamoCluster()
      Status.OK
    }
  }

  internal fun updateOurselfInDynamo() {
    val (duration, _) = timed {
      val self = dynamoCluster.snapshot.self.name
      val member = DyClusterMember()
      member.name = self
      member.updated_at = clock.instant().toEpochMilli()
      table.putItem(member)
    }

    logger.info { "Updated dynamodb with my information in ${duration.toMillis()}ms" }
  }

  internal fun recordCurrentDynamoCluster() {
    val (duration, _) = timed {
      val members = mutableSetOf<Member>()
      val threshold = clock.instant().minusSeconds(60).toEpochMilli()
      val request = ScanEnhancedRequest.builder()
        .consistentRead(true)
        .filterExpression(
          Expression.builder()
            .expression("updated_at >= :threshold")
            .expressionValues(mapOf(":threshold" to numberValue(threshold)))
            .build()
        )
        .build()
      for (page in table.scan(request).stream()) {
        for (item in page.items()) {
          members.add(Member(item.name!!, "invalid-ip"))
        }
      }

      dynamoCluster.update(members)
    }

    logger.info { "Updated cluster information from dynamodb in ${duration.toMillis()}ms" }
  }

  override fun shutDown() {}

  companion object {
    private val TABLE_SCHEMA = TableSchema.fromClass(DyClusterMember::class.java)
    private val logger = getLogger<DynamoClusterWatcherTask>()
  }
}

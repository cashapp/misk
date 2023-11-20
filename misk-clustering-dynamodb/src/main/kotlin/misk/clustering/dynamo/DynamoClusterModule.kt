package misk.clustering.dynamo

import com.google.inject.Provides
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.ServiceModule
import misk.clustering.Cluster
import misk.inject.KAbstractModule
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import java.util.UUID

class DynamoClusterModule : KAbstractModule() {
  override fun configure() {
    val dynamoCluster = DynamoCluster(System.getenv("MY_POD_NAME") ?: UUID.randomUUID().toString())
    bind<Cluster>().toInstance(dynamoCluster)
    bind<DynamoCluster>().toInstance(dynamoCluster)

    install(ServiceModule<DynamoClusterWatcherTask>())
    install(ServiceModule<RepeatedTaskQueue>(ForDynamoDbClusterWatching::class))
  }

  @Provides
  @ForDynamoDbClusterWatching
  @Singleton
  internal fun repeatedTaskQueue(queueFactory: RepeatedTaskQueueFactory): RepeatedTaskQueue {
    return queueFactory.new("dynamodb-cluster-watching")
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForDynamoDbClusterWatching

package misk.clustering.dynamo

import com.google.inject.Provides
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.clustering.Cluster
import misk.clustering.ClusterService
import misk.clustering.DefaultCluster
import misk.inject.AsyncSwitch
import misk.inject.DefaultAsyncSwitchModule
import misk.inject.KAbstractModule
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueFactory
import java.util.UUID

class DynamoClusterModule @JvmOverloads constructor(private val config: DynamoClusterConfig = DynamoClusterConfig()) : KAbstractModule() {
  override fun configure() {
    val defaultCluster = DefaultCluster(Cluster.Member(UUID.randomUUID().toString(), "invalid-ip"))
    bind<DynamoClusterConfig>().toInstance(config)
    bind<Cluster>().toInstance(defaultCluster)
    bind<DefaultCluster>().toInstance(defaultCluster)
    bind<ClusterService>().toInstance(defaultCluster)
    install(ServiceModule<ClusterService>())
    install(DefaultAsyncSwitchModule())
    install(
      ServiceModule<DynamoClusterWatcherTask>()
        .conditionalOn<AsyncSwitch>("clustering")
        .dependsOn<ClusterService>()
        .enhancedBy<ReadyService>()
    )
    install(ServiceModule<RepeatedTaskQueue>(ForDynamoDbClusterWatching::class))
  }

  @Provides
  @ForDynamoDbClusterWatching
  @Singleton
  internal fun repeatedTaskQueue(queueFactory: RepeatedTaskQueueFactory): RepeatedTaskQueue {
    return queueFactory.new("dynamodb-cluster-watch")
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForDynamoDbClusterWatching

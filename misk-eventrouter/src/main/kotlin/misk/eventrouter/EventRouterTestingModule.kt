package misk.eventrouter

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.moshi.MoshiAdapterModule
import misk.moshi.adapter
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @param distributed true if the caller may create multiple instances of EventRouter. In this case
 *     the caller is responsible for calling [EventRouter.joinCluster()] on each.
 */
class EventRouterTestingModule internal constructor(val distributed: Boolean) : KAbstractModule() {
  constructor() : this(false)

  private val actionExecutor = QueueingExecutorService()
  private val subscriberExecutor = QueueingExecutorService()

  @Singleton
  private class TestingService @Inject constructor() : AbstractIdleService() {
    @Inject private lateinit var eventRouter: RealEventRouter
    @Inject private lateinit var eventRouterTester: EventRouterTester
    @Inject private lateinit var clusterMapper: FakeClusterMapper
    override fun startUp() {
      clusterMapper.setOwnerForHostList(listOf("host_1"), "host_1")
      eventRouter.joinCluster()
      eventRouterTester.processEverything()
    }

    override fun shutDown() {
      eventRouter.leaveCluster()
    }
  }

  override fun configure() {
    if (distributed) {
      bind<EventRouter>().to<RealEventRouter>()
    } else {
      bind<EventRouter>().to<RealEventRouter>().asSingleton()
      bind<RealEventRouter>().asSingleton()
      install(ServiceModule<TestingService>())
    }

    bind<ClusterConnector>().to<FakeClusterConnector>()
    bind<ClusterMapper>().to<FakeClusterMapper>()
    install(MoshiAdapterModule(SocketEventJsonAdapter))
  }

  @Provides @Singleton @ForEventRouterActions
  internal fun actionQueueExecutor(): QueueingExecutorService {
    return actionExecutor
  }

  @Provides @Singleton @ForEventRouterActions
  internal fun actionExecutor(): ExecutorService {
    return actionExecutor
  }

  @Provides @Singleton @ForEventRouterSubscribers
  internal fun subscriberQueueExecutor(): QueueingExecutorService {
    return subscriberExecutor
  }

  @Provides @Singleton @ForEventRouterSubscribers
  internal fun subscriberExecutor(): ExecutorService {
    return subscriberExecutor
  }

  @Provides @Singleton
  internal fun provideEventJsonAdapter(moshi: Moshi) = moshi.adapter<SocketEvent>()
}

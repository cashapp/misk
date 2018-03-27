package misk.eventrouter

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.moshi.MoshiAdapterModule
import misk.moshi.adapter
import java.util.concurrent.ExecutorService
import javax.inject.Singleton

internal class EventRouterTestingModule : KAbstractModule() {
  private val actionExecutor = QueueingExecutorService()
  private val subscriberExecutor = QueueingExecutorService()

  override fun configure() {
    bind<EventRouter>().to<RealEventRouter>()
    bind<ClusterConnector>().to<FakeClusterConnector>()
    bind<ClusterMapper>().to<FakeClusterMapper>()
    install(MoshiAdapterModule(SocketEventJsonAdapter))
  }

  @Provides @Singleton @ForEventRouterActions
  fun actionQueueExecutor(): QueueingExecutorService {
    return actionExecutor
  }

  @Provides @Singleton @ForEventRouterActions
  fun actionExecutor(): ExecutorService {
    return actionExecutor
  }

  @Provides @Singleton @ForEventRouterSubscribers
  fun subscriberQueueExecutor(): QueueingExecutorService {
    return subscriberExecutor
  }

  @Provides @Singleton @ForEventRouterSubscribers
  fun subscriberExecutor(): ExecutorService {
    return subscriberExecutor
  }

  @Provides @Singleton
  internal fun provideEventJsonAdapter(moshi: Moshi) = moshi.adapter<SocketEvent>()
}

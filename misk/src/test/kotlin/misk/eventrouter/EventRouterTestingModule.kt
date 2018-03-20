package misk.eventrouter

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.moshi.MoshiAdapterModule
import misk.moshi.adapter
import java.util.concurrent.ExecutorService
import javax.inject.Singleton

internal class EventRouterTestingModule : KAbstractModule() {
  override fun configure() {
    bind<EventRouter>().to<RealEventRouter>()
    bind<ClusterConnector>().to<FakeClusterConnector>()
    bind<ClusterMapper>().to<AlphabeticalMapper>()
    bind<ExecutorService>()
        .annotatedWith<ForEventRouterSubscribers>()
        .to(QueueingExecutorService::class.java)
    install(MoshiAdapterModule(SocketEventJsonAdapter))
  }

  @Provides @Singleton
  internal fun provideEventJsonAdapter(moshi: Moshi) = moshi.adapter<SocketEvent>()
}

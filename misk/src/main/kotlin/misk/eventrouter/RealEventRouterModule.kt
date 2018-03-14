package misk.eventrouter

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.moshi.adapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

class RealEventRouterModule : KAbstractModule() {
  override fun configure() {
    bind<EventRouter>().to<RealEventRouter>().`in`(Singleton::class.java)
  }

  @Provides @Singleton @ForEventRouterSubscribers
  fun provideExecutor(): ExecutorService =
      ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES, LinkedBlockingQueue())

  @Provides @Singleton
  internal fun provideEventJsonAdapter(moshi: Moshi) = moshi.adapter<SocketEvent>()
}

/**
 * Annotates an executor service that'll be used to notify subscribers.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class ForEventRouterSubscribers

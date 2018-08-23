package misk.healthchecks

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import javax.inject.Qualifier
import javax.inject.Singleton

class ClusterWideHealthModule : KAbstractModule() {
  override fun configure() {
    // TODO(adrw) finish testing this with the new StaticResourceAction
    multibind<WebActionEntry>().toInstance(WebActionEntry<StaticResourceAction>("/health"))
    multibind<StaticResourceEntry>().toInstance(
        StaticResourceEntry("/health", "classpath:/admin/index.html"))

    multibind<WebActionEntry>().toInstance(WebActionEntry<ClusterWideHealthService>())
    multibind<Service>().to<ClusterWideHealthService>()
  }

  @Provides @Singleton @ForClusterWideHealthService
  fun healthServiceExecutor(): ScheduledExecutorService = Executors.newScheduledThreadPool(1)
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class ForClusterWideHealthService

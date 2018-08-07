package misk.healthchecks

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.resources.StaticResourceMapper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import javax.inject.Qualifier
import javax.inject.Singleton

class ClusterWideHealthModule : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(
        WebActionEntry<ClusterWideHealthPageAction>())
    multibind<WebActionEntry>().toInstance(
        WebActionEntry<ClusterWideHealthService>())
    multibind<Service>().to<ClusterWideHealthService>()
    multibind<StaticResourceMapper.Entry>()
        .toInstance(StaticResourceMapper.Entry("/admin/", "web/admin", "misk/web/admin/build"))
  }

  @Provides @Singleton @ForClusterWideHealthService
  fun healthServiceExecutor(): ScheduledExecutorService = Executors.newScheduledThreadPool(1)
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class ForClusterWideHealthService

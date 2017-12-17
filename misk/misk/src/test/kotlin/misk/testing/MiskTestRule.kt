package misk.web

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Provides
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.InjectionTestRule
import misk.web.jetty.JettyService
import okhttp3.HttpUrl
import org.junit.runners.model.FrameworkMethod
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class MiskTestRule(vararg modules: Module) : InjectionTestRule(
    WebModule(),
    MiskModule(),
    WebConfigModule(),
    *modules
) {
  private var jettyServerUrl : HttpUrl? = null

  fun serverUrl(): HttpUrl.Builder = jettyServerUrl!!.newBuilder()

  override fun beforeMethod(injector: Injector, method: FrameworkMethod, target: Any) {
    val serviceManager = injector.getInstance(ServiceManager::class.java)
    serviceManager.startAsync()
    serviceManager.awaitHealthy(5, TimeUnit.SECONDS)

    val jettyService = injector.getInstance(JettyService::class.java)
    jettyServerUrl = jettyService.serverUrl
  }

  override fun afterMethod(injector: Injector, method: FrameworkMethod, target: Any) {
    val serviceManager = injector.getInstance(ServiceManager::class.java)
    serviceManager.stopAsync()
    serviceManager.awaitStopped(5, TimeUnit.SECONDS)
  }

  class WebConfigModule : KAbstractModule() {
    override fun configure() {}

    @Provides
    @Singleton
    fun provideWebConfig() = WebConfig(0, 500000)
  }
}

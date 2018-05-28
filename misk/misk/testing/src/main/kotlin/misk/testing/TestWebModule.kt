package misk.testing

import com.google.inject.Provides
import misk.inject.KAbstractModule
import misk.resources.FakeResourceLoaderModule
import misk.web.WebConfig
import misk.web.WebSslConfig
import javax.inject.Singleton

class TestWebModule(private val ssl: WebSslConfig? = null) : KAbstractModule() {
  override fun configure() {
    install(FakeResourceLoaderModule())
  }

  @Provides
  @Singleton
  fun provideWebConfig() = WebConfig(0, 500000, host = "127.0.0.1", ssl = ssl)
}

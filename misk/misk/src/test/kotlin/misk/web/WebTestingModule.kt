package misk.web

import com.google.inject.Provides
import misk.MiskServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import javax.inject.Singleton

class WebTestingModule(private val ssl: WebSslConfig? = null) : KAbstractModule() {
  override fun configure() {
    install(EnvironmentModule(Environment.TESTING))
    install(MiskServiceModule())
    install(MiskWebModule())
  }

  @Provides
  @Singleton
  fun provideWebConfig() = WebConfig(0, 500000, host = "127.0.0.1", ssl = ssl)
}

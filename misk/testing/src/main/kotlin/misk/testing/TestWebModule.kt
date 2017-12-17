package misk.testing

import com.google.inject.Provides
import misk.inject.KAbstractModule
import misk.web.WebConfig
import javax.inject.Singleton

class TestWebModule : KAbstractModule() {
  override fun configure() {}

  @Provides
  @Singleton
  fun provideWebConfig() = WebConfig(0, 500000)
}

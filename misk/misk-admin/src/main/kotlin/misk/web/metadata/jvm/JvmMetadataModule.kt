package misk.web.metadata.jvm

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.moshi.adapter
import misk.web.WebActionModule
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

class JvmMetadataModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<JvmMetadataAction>())
  }

  @Provides fun provideRuntimeMxBean() : RuntimeMXBean {
    return ManagementFactory.getRuntimeMXBean()
  }
}

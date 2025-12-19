package misk.jvm

import com.google.inject.Provides
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import misk.inject.KInstallOnceModule

/** Default providers for the [ManagementFactory] beans that the framework depends on. */
// TODO deprecate and copy this provider code to ConfigDashboardTabModule, it doesn't need to be a public API
class JvmManagementFactoryModule : KInstallOnceModule() {
  @Provides
  fun provideRuntimeMxBean(): RuntimeMXBean {
    return ManagementFactory.getRuntimeMXBean()
  }
}

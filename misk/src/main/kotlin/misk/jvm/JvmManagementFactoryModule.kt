package misk.jvm

import com.google.inject.Provides
import misk.inject.KInstallOnceModule
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

/**
 * Default providers for the [ManagementFactory] beans that the framework depends on.
 */
class JvmManagementFactoryModule : KInstallOnceModule() {
  @Provides fun provideRuntimeMxBean() : RuntimeMXBean {
    return ManagementFactory.getRuntimeMXBean()
  }
}

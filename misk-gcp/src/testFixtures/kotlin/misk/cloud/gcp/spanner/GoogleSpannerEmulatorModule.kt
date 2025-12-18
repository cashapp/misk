package misk.cloud.gcp.spanner

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.testing.TestFixture

/** Runs an in-memory version of Google Spanner using Docker. */
class GoogleSpannerEmulatorModule(private val config: SpannerConfig) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<GoogleSpannerEmulator>().dependsOn<GoogleSpannerService>())

    val emulator = GoogleSpannerEmulator(config)
    bind(keyOf<GoogleSpannerEmulator>()).toInstance(emulator)
    multibind<TestFixture>().toInstance(emulator)
  }
}

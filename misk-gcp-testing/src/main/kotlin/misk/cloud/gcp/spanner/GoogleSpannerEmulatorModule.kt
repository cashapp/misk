package misk.cloud.gcp.spanner

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf

/** Runs an in-memory version of Google Spanner using Docker. */
@Deprecated("Replace the dependency on misk-gcp-testing with testFixtures(misk-gcp)")
class GoogleSpannerEmulatorModule(
  private val config: SpannerConfig,
): KAbstractModule() {
  override fun configure() {
    install(
      ServiceModule<GoogleSpannerEmulator>()
        .dependsOn<GoogleSpannerService>()
    )
    bind(keyOf<GoogleSpannerEmulator>()).toInstance(
      GoogleSpannerEmulator(config)
    )
  }
}

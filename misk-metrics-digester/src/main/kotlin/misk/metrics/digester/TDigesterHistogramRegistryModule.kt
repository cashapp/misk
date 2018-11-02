package misk.metrics.digester

import misk.inject.KAbstractModule
import misk.metrics.HistogramRegistry

class TDigesterHistogramRegistryModule : KAbstractModule() {
  override fun configure() {
    bind<HistogramRegistry>().to<TDigestHistogramRegistry<VeneurDigest>>()
  }
}
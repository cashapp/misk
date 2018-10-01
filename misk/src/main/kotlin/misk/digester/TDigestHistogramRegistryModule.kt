package misk.digester

import misk.inject.KAbstractModule
import misk.metrics.Histogram
import misk.metrics.HistogramRegistry

class TDigestHistogramRegistryModule : KAbstractModule() {

  override fun configure() {
    bind<HistogramRegistry>().to<TDigestHistogramRegistry>()
    bind<Histogram>().to<TDigestHistogram>()
  }
}
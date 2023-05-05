package misk.crypto.internal

import com.google.inject.Inject
import com.google.inject.Provider
import misk.crypto.CryptoConfig
import misk.metrics.v2.Metrics

class KeyMetricsProvider(val config: CryptoConfig?): Provider<KeyMetrics> {

  @Inject lateinit var metrics: Metrics

  override fun get(): KeyMetrics {
    return KeyMetrics(metrics, config)
  }

}

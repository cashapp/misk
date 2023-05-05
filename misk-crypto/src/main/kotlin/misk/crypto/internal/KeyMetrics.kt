package misk.crypto.internal

import com.google.inject.Inject
import io.prometheus.client.Counter
import io.prometheus.client.Summary
import misk.crypto.CryptoConfig
import misk.crypto.Key
import misk.metrics.v2.Metrics

enum class KeyMetricType {
  crypto_key_loaded_total,
  crypto_key_op_total,
  crypto_key_op_bytes
}

enum class KeyMetricOp {
  encrypted,
  decrypted,
  verified,
  signed
}

/**
 * Client side metrics for `misk-crypto` tracking keys loaded, encrypt/decrypt operations and
 * bytes on the tink primitives via decorators in this package.
 */
class KeyMetrics @Inject constructor(
  val metrics: Metrics,
  val config: CryptoConfig?
) {

  private val loadCounter: Counter = metrics.counter(
    KeyMetricType.crypto_key_loaded_total.name,
    "Key loaded as either encrypted or cleartext.",
    listOf("type", "alias", "clear_text")
  )
  // @todo We can derive this from the `summary_count`, but will lose incidents after the
  // `ln_metric_age_in_seconds` but do we care? If not let's drop this guy.
  private val opCounter: Counter = metrics.counter(
    KeyMetricType.crypto_key_op_total.name,
    "Key was used in op.",
    listOf("type", "alias", "op")
  )
  private val lnSummary: Summary = metrics.summary(
    name = KeyMetricType.crypto_key_op_bytes.name,
    help = "Length of bytes in or out for encrypt, decrypt or verify.",
    labelNames = listOf("type", "alias", "op"),
    maxAgeSeconds = config?.ln_metric_age_in_seconds
  )

  /**
   * Builds the encrypt/decrypt {@link Counter}s for the specified {@link KeyAlias}.
   */
  fun loaded(key: Key, wasClearTxt: Boolean) {
    loadCounter.labels(key.key_type.name, key.key_name, wasClearTxt.toString()).inc()
  }

  /**
   * Key and the length encrypted
   */
  fun encrypted(key: Key, ln: Int) {
    op(key, ln, KeyMetricOp.encrypted)
  }

  /**
   * Key and the length decrypted
   */
  fun decrypted(key: Key, ln: Int) {
    op(key, ln, KeyMetricOp.decrypted)
  }

  /**
   * Key and the length verified
   */
  fun verified(key: Key, ln: Int) {
    op(key, ln, KeyMetricOp.verified)
  }

  fun signed(key: Key, ln: Int) {
    op(key, ln, KeyMetricOp.signed)
  }

  fun op(key: Key, ln: Int, op: KeyMetricOp) {
    opCounter.labels(key.key_type.name, key.key_name, op.name).inc()
    lnSummary.labels(key.key_type.name, key.key_name, op.name).observe(ln.toDouble())
  }

}

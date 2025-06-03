package misk.metrics.v2

import io.prometheus.client.Collector
import io.prometheus.client.SimpleCollector
import java.lang.ref.WeakReference
import javax.annotation.concurrent.ThreadSafe

/**
 * A [ProvidedGauge] is a variant of a [io.prometheus.client.Gauge]  that allows you to
 * register a provider for gauge value. The provider function is called when the gauge value
 * is collected. This is useful for tracking values that are already being tracked. The gauge
 * will hold a weak reference to the provider object, so it will not prevent it from being garbage
 * collected. If the provider object is garbage collected, the gauge will return 0.
 */
@ThreadSafe
class ProvidedGauge private constructor(
  builder: Builder
) : SimpleCollector<ProvidedGauge.Child>(builder) {
  class Builder : SimpleCollector.Builder<Builder, ProvidedGauge>() {

    override fun create(): ProvidedGauge {
      return ProvidedGauge(this)
    }
  }

  /** Convenience method for registering a provider without labels */
  fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) {
    noLabelsChild.registerProvider(reference, provider)
  }

  class Child {
    var reference: WeakReference<Any> = WeakReference(null)
      private set
    private var provider: () -> Number = { 0 }
    fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) {
      this.reference = WeakReference(reference)
      this.provider = {
        this.reference.get()?.let {
          @Suppress("UNCHECKED_CAST")
          it as? T
        }?.provider() ?: 0
      }
    }

    fun get(): Double {
      return provider().toDouble()
    }
  }

  override fun collect(): MutableList<MetricFamilySamples> {
    val samples = ArrayList<MetricFamilySamples.Sample>(children.size)
    for ((labels, value) in children.entries) {
      samples.add(MetricFamilySamples.Sample(fullname, labelNames, labels, value.get()))
    }
    return familySamplesList(Type.GAUGE, samples)
  }

  override fun <T : Collector?> setChild(child: Child?, vararg labelValues: String?): T {
    return super.setChild(child, *labelValues)
  }

  override fun newChild(): Child {
    return Child()
  }

  companion object {
    fun builder(name: String, help: String): Builder {
      return Builder().name(name).help(help)
    }
  }
}

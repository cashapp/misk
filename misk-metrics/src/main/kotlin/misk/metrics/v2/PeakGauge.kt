package misk.metrics.v2

import com.google.common.util.concurrent.AtomicDouble
import io.prometheus.client.SimpleCollector
import javax.annotation.concurrent.ThreadSafe

/**
 * A peak gauge is a variant of a [io.prometheus.client.Gauge] that resets to an initial value of 0
 * after a metric collection.
 *
 * This is useful for accurately capturing maximum observed values over time. In contrast to the
 * histogram maximum which tracks the maximum value in its sampling window. That sampling window
 * typically covers multiple metric collections.
 */
@ThreadSafe
class PeakGauge : SimpleCollector<PeakGauge.Child> {
  constructor(builder: Builder) : super(builder)

  class Builder : SimpleCollector.Builder<Builder, PeakGauge>() {
    override fun create(): PeakGauge {
      return PeakGauge(this)
    }
  }

  /** Convenience method for recording values without labels */
  fun record(newValue: Double) {
    noLabelsChild.record(newValue)
  }

  class Child {
    // For simplicity, using an atomic double with an initial value of 0 here.
    //
    // If we cared to differentiate between the initial value due to no samples vs having
    // samples that are equal or less than 0, then we would need to also track whether we
    // have received an update since the last reset.
    private val value = AtomicDouble()

    /**
     * Updates the stored value if the new value is greater.
     */
    fun record(newValue: Double) {
      while (true) {
        val previous = value.get()
        if (newValue > previous) {
          value.compareAndSet(previous, newValue)
        } else {
          return
        }
      }
    }

    /**
     * Reset to the initial value and return previously held value.
     */
    fun getAndClear(): Double {
      return value.getAndSet(0.0)
    }
  }

  override fun collect(): MutableList<MetricFamilySamples> {
    val samples = ArrayList<MetricFamilySamples.Sample>(children.size)
    for ((labels, value) in children.entries) {
      samples.add(MetricFamilySamples.Sample(fullname, labelNames, labels, value.getAndClear()))
    }
    return familySamplesList(Type.GAUGE, samples)
  }

  override fun newChild(): Child {
    return Child()
  }

  companion object {
    fun builder(name: String, help: String) : Builder {
      return Builder().name(name).help(help)
    }
  }
}

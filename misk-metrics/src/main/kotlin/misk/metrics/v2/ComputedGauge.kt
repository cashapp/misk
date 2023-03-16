import io.prometheus.client.SimpleCollector
import wisp.logging.getLogger
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
class ComputedGauge constructor(
  builder: Builder,
  private val computeFun: ValueSupplier
) : SimpleCollector<ComputedGauge.Child>(builder) {
  class Builder(private val valueSupplier: ValueSupplier) : SimpleCollector.Builder<Builder, ComputedGauge>() {
    override fun create(): ComputedGauge {

      return ComputedGauge(this, valueSupplier)
    }
  }

  class Child(private val valueSupplier: ValueSupplier) {
    fun getValue(labelNames: List<String>, labelValues: List<String>): Double {
      return valueSupplier.get(labelNames, labelValues)
    }
  }

  override fun collect(): MutableList<MetricFamilySamples> {
    val samples = ArrayList<MetricFamilySamples.Sample>(children.size)
    for ((labelValues, child) in children.entries) {
      try {
        val computedValue = child.getValue(labelNames, labelValues)
        samples.add(MetricFamilySamples.Sample(fullname, labelNames, labelValues, computedValue))
      } catch (e: Exception) {
        logger.error(e) {
          "Exception computing gauge value. name=${this.fullname}, " +
            "labelNames=$labelNames, labelValues=$labelValues"
        }
      }
    }
    return familySamplesList(Type.GAUGE, samples)
  }

  override fun newChild(): Child {
    return Child(computeFun)
  }

  companion object {
    fun builder(name: String, help: String, valueSupplier: ValueSupplier) : Builder {
      val builder = Builder(valueSupplier).name(name).help(help)
    }

    private val logger = getLogger<ComputedGauge>()
  }

  /**
   * TODO real docs
   * - Implementation _must_ be thread-safe. Ideally non-locking.
   * - Implementation _must_ be non-blocking. Do not do significant work in value computation (
   * prefer setting such values periodically instead)
   */
  fun interface ValueSupplier {
    fun get(labelNames: List<String>, labelValues: List<String>): Double
  }
}

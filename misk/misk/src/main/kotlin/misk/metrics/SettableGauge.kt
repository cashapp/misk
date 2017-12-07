package misk.metrics

import com.codahale.metrics.Gauge
import java.util.concurrent.atomic.AtomicLong

class SettableGauge : Gauge<Long> {
  val value = AtomicLong()

  override fun getValue(): Long = value.get()
}

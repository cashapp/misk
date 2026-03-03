package misk.metrics.digester

import org.assertj.core.api.AbstractDoubleAssert

/**
 * AssertJ 3.15 broke NaN: https://github.com/joel-costigliola/assertj-core/issues/1783
 *
 * TODO: replace with isEqualTo() when AssertJ 3.16 is released.
 */
internal fun AbstractDoubleAssert<*>.isEqualToHonorNan(expected: Double) {
  when {
    java.lang.Double.isNaN(expected) -> isNaN()
    else -> isEqualTo(expected)
  }
}

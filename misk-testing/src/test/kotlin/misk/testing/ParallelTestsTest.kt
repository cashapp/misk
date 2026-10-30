package misk.testing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ParallelTestsTest {
  @Test
  fun gradleWorkerId() {
    withWorkerId("123") { assertThat(gradleTestWorkerId()).isEqualTo("123") }
  }

  @Test
  fun outsideGradle() {
    withWorkerId(null) { assertThat(gradleTestWorkerId()).isNull() }
  }

  private fun withWorkerId(workerId: String?, test: () -> Unit) {
    val original = System.getProperty("org.gradle.test.worker")
    try {
      if (workerId == null) System.clearProperty("org.gradle.test.worker")
      else System.setProperty("org.gradle.test.worker", workerId)
      test()
    } finally {
      if (original == null) System.clearProperty("org.gradle.test.worker")
      else System.setProperty("org.gradle.test.worker", original)
    }
  }
}

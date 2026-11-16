package misk.testing

/**
 * Identifies the current Gradle test worker, or returns null when running outside Gradle.
 *
 * The ID is unique within a Gradle invocation, including across different test tasks and replacement workers. Use it to
 * namespace shared test resources. It is not bounded by `maxParallelForks` and must not be used as a Redis database
 * number or another index into a fixed set of resources. Separate Gradle invocations may reuse the same worker IDs.
 */
fun gradleTestWorkerId(): String? = System.getProperty("org.gradle.test.worker")

/** Provides helper methods for supporting tests running in parallel. */
internal object ParallelTests {

  /**
   * Can be used for determining if tests are running in parallel (with Gradle's `maxParallelForks`), and returns a
   * bounded numeric partition ID for the current test process.
   *
   * This assumes that the environment variable `MAX_TEST_PARALLEL_FORKS` is set to the number of `maxParallelForks`.
   */
  internal fun isPartitioned(): PartitionedTest {
    val maxTestPartitions = System.getenv("MAX_TEST_PARALLEL_FORKS")?.toInt()
    return if (maxTestPartitions != null && maxTestPartitions > 1) {
      val testWorkerId = System.getProperty("org.gradle.test.worker", "0").toInt()
      val partitionId = ((testWorkerId % maxTestPartitions) + 1)
      PartitionedTest.Partitioned(partitionId)
    } else {
      PartitionedTest.NotPartitioned
    }
  }
}

internal sealed interface PartitionedTest {
  data object NotPartitioned : PartitionedTest

  data class Partitioned(val partitionId: Int) : PartitionedTest
}

/**
 * Applies the `update` lambda to the given `T`, if the tests are running in parallel (with Gradle's
 * `maxParallelForks`).
 *
 * The partition index is bounded by `MAX_TEST_PARALLEL_FORKS`. Different test tasks or replacement workers can map to
 * the same index. Use [gradleTestWorkerId] when naming shared resources that require a unique ID within the build.
 *
 * This assumes that the environment variable `MAX_TEST_PARALLEL_FORKS` is set to the number of `maxParallelForks`.
 */
fun <T> T.updateForParallelTests(update: (T, Int) -> T): T = parallelTestIndex()?.let { update(this, it) } ?: this

fun parallelTestIndex(): Int? {
  return when (val partitionedTest = ParallelTests.isPartitioned()) {
    is PartitionedTest.Partitioned -> {
      partitionedTest.partitionId
    }

    is PartitionedTest.NotPartitioned -> null
  }
}

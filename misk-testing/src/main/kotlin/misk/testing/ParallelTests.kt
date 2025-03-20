package misk.testing

/**
 * Provides helper methods for supporting tests running in parallel.
 */
internal object ParallelTests {

  /**
   * Can be used for determining if tests are running in parallel (with Gradle's `maxParallelForks`),
   * and returns a unique numeric partition ID for the current test process.
   *
   * This assumes that the environment variable `MAX_TEST_PARALLEL_FORKS` is set to the number of `maxParallelForks`.
   */
  internal fun partitioned(): PartitionedTest {
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
 * Applies the `update` function to the given `T`, if the tests are running in parallel
 * (with Gradle's `maxParallelForks`). The `update` function is also passed the partition ID.
 *
 * This can be used for updating configurations for the purpose of providing isolation between
 * tests running across different parallel processes, such as appending the partition ID to the database name,
 * so that it's unique for each process.
 *
 * This assumes that the environment variable `MAX_TEST_PARALLEL_FORKS` is set to the number of `maxParallelForks`.
 */
fun <T> T.updateForParallelTests(update: (T, Int) -> T): T {
  return when (val partitionedTest = ParallelTests.partitioned()) {
    is PartitionedTest.Partitioned -> {
      update(this, partitionedTest.partitionId)
    }

    is PartitionedTest.NotPartitioned -> this
  }
}


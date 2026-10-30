package misk.jdbc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.ClearEnvironmentVariable
import org.junitpioneer.jupiter.ClearSystemProperty
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.junitpioneer.jupiter.SetSystemProperty

class PartitionedDataSourceConfigTest {
  private val config = DataSourceConfig(type = DataSourceType.MYSQL, database = "test", username = "root")

  @Test
  @SetEnvironmentVariable(key = "MAX_TEST_PARALLEL_FORKS", value = "4")
  @SetSystemProperty(key = "org.gradle.test.worker", value = "1")
  fun workersWithTheSameModuloHaveDifferentDatabases() {
    val first = config.updateForParallelTests()
    System.setProperty("org.gradle.test.worker", "5")
    val fifth = config.updateForParallelTests()

    assertThat(first).isEqualTo(config.copy(database = "test_worker_1"))
    assertThat(fifth).isEqualTo(config.copy(database = "test_worker_5"))
    assertThat(first.database).isNotEqualTo(fifth.database)
  }

  @Test
  @SetSystemProperty(key = "org.gradle.test.worker", value = "123")
  fun writerAndReaderUseTheSameWorker() {
    val reader = config.copy(host = "reader", username = "readonly")
    val cluster = DataSourceClusterConfig(writer = config, reader = reader)

    assertThat(cluster.updateForParallelTests())
      .isEqualTo(
        cluster.copy(
          writer = config.copy(database = "test_worker_123"),
          reader = reader.copy(database = "test_worker_123"),
        )
      )
  }

  @Test
  @SetSystemProperty(key = "org.gradle.test.worker", value = "123")
  fun clusterWithoutReader() {
    val cluster = DataSourceClusterConfig(writer = config, reader = null)

    assertThat(cluster.updateForParallelTests())
      .isEqualTo(cluster.copy(writer = config.copy(database = "test_worker_123")))
  }

  @Test
  @ClearSystemProperty(key = "org.gradle.test.worker")
  @SetEnvironmentVariable(key = "MAX_TEST_PARALLEL_FORKS", value = "4")
  fun outsideGradle() {
    val cluster = DataSourceClusterConfig(writer = config, reader = config)

    assertThat(config.updateForParallelTests()).isSameAs(config)
    assertThat(cluster.updateForParallelTests()).isEqualTo(cluster)
  }

  @Test
  @SetSystemProperty(key = "org.gradle.test.worker", value = "123")
  @SetEnvironmentVariable(key = "MAX_TEST_PARALLEL_FORKS", value = "1")
  fun singleForkTaskStillGetsItsOwnDatabase() {
    assertThat(config.updateForParallelTests()).isEqualTo(config.copy(database = "test_worker_123"))
  }

  @Test
  @SetSystemProperty(key = "org.gradle.test.worker", value = "123")
  @ClearEnvironmentVariable(key = "MAX_TEST_PARALLEL_FORKS")
  fun doesNotRequireParallelForkConfiguration() {
    assertThat(config.updateForParallelTests()).isEqualTo(config.copy(database = "test_worker_123"))
  }
}

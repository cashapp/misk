package misk.vitess.testing.utilities

import misk.testing.ExternalDependency
import misk.vitess.testing.VitessTestDb

/**
 * A lightweight wrapper around `VitessTestDb` that implements the `ExternalDependency` interface.
 * 
 * For general usage with Gradle, it's recommended to instead use the [Vitess database Gradle plugin](misk/misk-vitess-database-gradle-plugin/README.md).
 */
object DockerVitess : ExternalDependency {
  private val vitessTestDb = VitessTestDb(containerName = "vitess_test_db_ext")

  override fun startup() {
    vitessTestDb.run()
  }

  override fun shutdown() {
    // no-op
  }

  override fun beforeEach() {
    vitessTestDb.truncate()
  }

  override fun afterEach() {
    // no-op
  }
}

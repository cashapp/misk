package misk.vitess.testing.utilities

import misk.testing.ExternalDependency
import misk.vitess.testing.DefaultSettings
import misk.vitess.testing.VitessTestDb

/**
 * A lightweight wrapper around `VitessTestDb` that implements the `ExternalDependency` interface.
 * 
 * For general usage with Gradle, it's recommended to instead use the [Vitess database Gradle plugin](misk/misk-vitess-database-gradle-plugin/README.md).
 */
class DockerVitess(
  /**
   * Whether to enable scatter queries. It's recommended to disable scatter queries by default
   * in tests (i.e. set `enableScatters` = `false`), and opt-in queries via the Vitess query
   * hint to allow scatters (see [misk.vitess.VitessQueryHints.allowScatter]).
   */
  enableScatters: Boolean = true,
  /**
   * The name of the Vitess container. This is used to identify the container in Docker.
   */
  containerName: String = DefaultSettings.CONTAINER_NAME,
  /**
   * The port to connect to the database, which represents the vtgate.
   */
  port: Int = DefaultSettings.PORT
) : ExternalDependency {

  private val vitessTestDb = VitessTestDb(
    containerName = containerName,
    enableScatters = enableScatters,
    port = port)

  override fun startup() {
    vitessTestDb.run()
  }

  override fun shutdown() {
  }

  override fun beforeEach() {
    vitessTestDb.truncate()
  }

  override fun afterEach() {
    // no-op
  }

  override val id: String
    get() = vitessTestDb.containerName
}

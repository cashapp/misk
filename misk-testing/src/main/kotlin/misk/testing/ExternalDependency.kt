package misk.testing

/**
 * An external dependency of the Misk Application that needs to be started for test, like Redis,
 * Vitess, MySQL, SQS, etc.
 */
interface ExternalDependency {
  /**
   * Starts the dependency.
   */
  fun startup()

  /**
   * Stops the dependency.
   */
  fun shutdown()

  /**
   * Called before each test run
   */
  fun beforeEach()

  /**
   * Called before each test run
   */
  fun afterEach()

  /**
   * Unique ID for the dependency, used as a stable key across tests. Can be overridden if more
   * than one instance of the dependency is supported.
   */
  val id: String get() = javaClass.name
}

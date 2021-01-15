package misk.database

/**
 * Represents a database server for development/testing generally running in Docker.
 */
interface DatabaseServer {
  fun stop()
  fun start()
  fun pullImage()
}

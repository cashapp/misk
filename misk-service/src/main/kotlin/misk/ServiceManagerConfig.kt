package misk

import wisp.config.Config

@Suppress("PropertyName")
data class ServiceManagerConfig(
  /**
   * If true, writes the full graph of [ServiceModule] services and their dependencies to info-level
   * logs.
   */
  val debug_service_graph: Boolean = false
): Config

package misk.hibernate

import com.google.common.util.concurrent.Service

/**
 * Marker interface for services that provide database transactors.
 *
 * Services that require a database connection should depend on this interface when they are
 * installed in a module.
 *
 * e.g.
 *
 * ```
 * install(ServiceModule<MoviesService>()
 *     .dependsOn<TransacterService>(Movies::class))
 * ```
 **/
interface TransacterService : Service {
  fun registerTransacter(transacter: Transacter)
}

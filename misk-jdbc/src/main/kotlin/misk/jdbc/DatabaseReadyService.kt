package misk.jdbc

import com.google.common.util.concurrent.Service

/**
 * Marker interface for indicating that the database is ready.
 *
 * Services that require a database connection should depend on this interface when they are
 * installed in a module.
 *
 * e.g.
 *
 * ```
 * install(ServiceModule<MoviesService>()
 *     .dependsOn<DatabaseReadyService>(Movies::class))
 * ```
 **/
interface DatabaseReadyService : Service
